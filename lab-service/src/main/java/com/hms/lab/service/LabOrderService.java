package com.hms.lab.service;

import com.hms.lab.dto.*;
import com.hms.lab.entity.LabOrder;
import com.hms.lab.entity.LabTest;
import com.hms.lab.entity.OrderTest;
import com.hms.lab.event.BillingLabEventPublisher;
import com.hms.lab.exception.BusinessException;
import com.hms.lab.exception.ResourceNotFoundException;
import com.hms.lab.repository.LabOrderRepository;
import com.hms.lab.repository.LabTestRepository;
import com.hms.lab.repository.OrderTestRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final OrderTestRepository orderTestRepository;
    private final MinioService minioService;
    private final BillingLabEventPublisher billingLabEventPublisher;

    // ─── Create Order ─────────────────────────────────────────────────────────────

    @Transactional
    public LabOrderDto createOrder(LabOrderCreateRequest request, String createdBy) {
        // Validate patient info
        boolean hasPatientId = request.getPatientId() != null;
        boolean hasPatientName = request.getPatientName() != null && !request.getPatientName().isBlank();

        if (!hasPatientId && !hasPatientName) {
            throw new BusinessException("MISSING_PATIENT_INFO",
                    "Either patient_id or patient_name must be provided", 400);
        }

        // Resolve patient name for denormalization
        String patientName = hasPatientName ? request.getPatientName() : "Patient " + request.getPatientId();

        // Idempotency for clinical_request via session_id
        if ("clinical_request".equals(request.getSource()) && request.getSessionId() != null) {
            labOrderRepository.findBySessionId(request.getSessionId()).ifPresent(existing -> {
                log.warn("Order already exists for session {}, skipping.", request.getSessionId());
                throw new BusinessException("DUPLICATE_ORDER",
                        "Order already exists for session: " + request.getSessionId(), 409);
            });
        }

        // Build order and order tests
        LabOrder order = LabOrder.builder()
                .patientId(request.getPatientId())
                .patientName(patientName)
                .patientMobile(request.getPatientMobile())
                .sessionId(request.getSessionId())
                .source(request.getSource() != null ? request.getSource() : "walk_in")
                .status("registered")
                .paymentStatus("pending")
                .notes(request.getNotes())
                .createdBy(createdBy)
                .build();

        List<OrderTest> orderTests = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderTestRequest testReq : request.getTests()) {
            LabTest labTest = labTestRepository.findById(testReq.getTestId())
                    .orElseThrow(() -> new BusinessException("INVALID_TEST_ID",
                            "Test not found: " + testReq.getTestId(), 400));

            if (!Boolean.TRUE.equals(labTest.getIsActive())) {
                throw new BusinessException("INACTIVE_TEST",
                        "Test is not active: " + labTest.getName(), 400);
            }

            OrderTest orderTest = OrderTest.builder()
                    .order(order)
                    .testId(labTest.getId())
                    .testName(labTest.getName())
                    .testCode(labTest.getCode())
                    .urgency(testReq.getUrgency() != null ? testReq.getUrgency() : "routine")
                    .unitPrice(labTest.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                    .status("pending")
                    .build();

            orderTests.add(orderTest);
            totalAmount = totalAmount.add(labTest.getUnitPrice());
        }

        order.setTests(orderTests);
        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));

        LabOrder saved = labOrderRepository.save(order);
        log.info("Created lab order {} for patient {}", saved.getId(), saved.getPatientName());
        return toDto(saved);
    }

    // ─── List Orders ──────────────────────────────────────────────────────────────

    public Page<LabOrderDto> listOrders(UUID patientId, String status, String paymentStatus,
                                         String source, LocalDate date, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<LabOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentStatus != null && !paymentStatus.isBlank()) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (date != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        date.atStartOfDay(ZoneId.systemDefault())));
                predicates.add(cb.lessThan(root.get("createdAt"),
                        date.plusDays(1).atStartOfDay(ZoneId.systemDefault())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return labOrderRepository.findAll(spec, pageable).map(this::toDto);
    }

    // ─── Get Order by ID ──────────────────────────────────────────────────────────

    public LabOrderDto getOrderById(UUID id) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toDto(order);
    }

    // ─── Enter Results ────────────────────────────────────────────────────────────

    @Transactional
    public LabOrderDto enterResults(UUID orderId, ResultsUpdateRequest request) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if ("cancelled".equals(order.getStatus())) {
            throw new BusinessException("ORDER_CANCELLED", "Cannot enter results for a cancelled order", 422);
        }
        if ("completed".equals(order.getStatus())) {
            throw new BusinessException("ORDER_COMPLETED", "Order is already completed", 422);
        }

        // Update specified order tests
        for (ResultItem resultItem : request.getResults()) {
            OrderTest orderTest = orderTestRepository.findById(resultItem.getOrderTestId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order test not found: " + resultItem.getOrderTestId()));

            if (!orderTest.getOrder().getId().equals(orderId)) {
                throw new BusinessException("INVALID_ORDER_TEST",
                        "Order test does not belong to this order", 400);
            }

            orderTest.setResultValue(resultItem.getResultValue());
            orderTest.setIsAbnormal(resultItem.getIsAbnormal());
            orderTest.setTechnicianNotes(resultItem.getTechnicianNotes());
            orderTest.setStatus("completed");
            orderTestRepository.save(orderTest);
        }

        // Refresh order and determine new status
        LabOrder refreshed = labOrderRepository.findById(orderId).orElseThrow();
        boolean allCompleted = refreshed.getTests().stream()
                .allMatch(t -> "completed".equals(t.getStatus()));

        if (allCompleted) {
            refreshed.setStatus("completed");
            refreshed.setCompletedAt(ZonedDateTime.now());
            log.info("All tests completed for order {}", orderId);
        } else {
            refreshed.setStatus("processing");
        }

        LabOrder saved = labOrderRepository.save(refreshed);
        log.info("Results entered for order {}. Status: {}", orderId, saved.getStatus());
        return toDto(saved);
    }

    // ─── Upload Report ────────────────────────────────────────────────────────────

    @Transactional
    public ReportUploadResponse uploadReport(UUID orderId, MultipartFile file, String notes) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if ("cancelled".equals(order.getStatus())) {
            throw new BusinessException("ORDER_CANCELLED", "Cannot upload report for a cancelled order", 422);
        }

        String objectKey = "reports/" + orderId + "/" + UUID.randomUUID() + ".pdf";

        try {
            minioService.uploadReport(objectKey, file.getInputStream(), file.getSize());
        } catch (Exception e) {
            log.error("Failed to upload report for order {}: {}", orderId, e.getMessage(), e);
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload report PDF", 500);
        }

        order.setReportFileKey(objectKey);
        order.setReportNotes(notes);
        order.setStatus("completed");
        order.setCompletedAt(ZonedDateTime.now());

        labOrderRepository.save(order);
        log.info("Report uploaded for order {}. Key: {}", orderId, objectKey);

        String presignedUrl = minioService.generatePresignedUrl(objectKey, 15);
        ZonedDateTime expiresAt = ZonedDateTime.now().plusMinutes(15);

        return ReportUploadResponse.builder()
                .orderId(orderId)
                .reportUrl(presignedUrl)
                .expiresAt(expiresAt)
                .status("completed")
                .build();
    }

    // ─── Record Payment ───────────────────────────────────────────────────────────

    @Transactional
    public LabOrderDto recordPayment(UUID orderId, PaymentRequest request) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if ("paid".equals(order.getPaymentStatus())) {
            throw new BusinessException("ALREADY_PAID", "Order is already paid", 422);
        }

        order.setPaymentStatus("paid");
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaidAt(ZonedDateTime.now());

        LabOrder saved = labOrderRepository.save(order);
        log.info("Payment recorded for order {}. Method: {}", orderId, request.getPaymentMethod());

        // Publish billing.lab event to Finance Service
        billingLabEventPublisher.publishBillingLab(saved);

        return toDto(saved);
    }

    // ─── Patient Lab History ──────────────────────────────────────────────────────

    public Page<LabOrderDto> getPatientHistory(UUID patientId, LocalDate from, LocalDate to,
                                                int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<LabOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("patientId"), patientId));

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        from.atStartOfDay(ZoneId.systemDefault())));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        to.plusDays(1).atStartOfDay(ZoneId.systemDefault())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return labOrderRepository.findAll(spec, pageable).map(this::toDto);
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────────

    public LabOrderDto toDto(LabOrder order) {
        // Generate presigned URL if report exists
        String reportUrl = null;
        if (order.getReportFileKey() != null && !order.getReportFileKey().isBlank()) {
            try {
                reportUrl = minioService.generatePresignedUrl(order.getReportFileKey(), 15);
            } catch (Exception e) {
                log.warn("Could not generate presigned URL for order {}: {}", order.getId(), e.getMessage());
            }
        }

        return LabOrderDto.builder()
                .id(order.getId())
                .patientId(order.getPatientId())
                .patientName(order.getPatientName())
                .patientMobile(order.getPatientMobile())
                .sessionId(order.getSessionId())
                .source(order.getSource())
                .tests(order.getTests().stream().map(this::toOrderTestDto).toList())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .reportFileUrl(reportUrl)
                .reportNotes(order.getReportNotes())
                .createdBy(order.getCreatedBy())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .build();
    }

    private OrderTestDto toOrderTestDto(OrderTest ot) {
        return OrderTestDto.builder()
                .id(ot.getId())
                .orderId(ot.getOrder() != null ? ot.getOrder().getId() : null)
                .testId(ot.getTestId())
                .testName(ot.getTestName())
                .testCode(ot.getTestCode())
                .urgency(ot.getUrgency())
                .unitPrice(ot.getUnitPrice())
                .resultValue(ot.getResultValue())
                .isAbnormal(ot.getIsAbnormal())
                .technicianNotes(ot.getTechnicianNotes())
                .status(ot.getStatus())
                .build();
    }
}
