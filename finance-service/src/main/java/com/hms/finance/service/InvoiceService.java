package com.hms.finance.service;

import com.hms.finance.dto.*;
import com.hms.finance.entity.Invoice;
import com.hms.finance.entity.LineItem;
import com.hms.finance.exception.BusinessException;
import com.hms.finance.exception.ResourceNotFoundException;
import com.hms.finance.repository.InvoiceRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    // ─── Invoice Number Generator ───────────────────────────────────────────────

    @Transactional
    public synchronized String generateInvoiceNumber() {
        String year = String.valueOf(Year.now().getValue());
        int maxSeq = invoiceRepository.findMaxSequenceForYear(year);
        int nextSeq = maxSeq + 1;
        return String.format("INV-%s-%05d", year, nextSeq);
    }

    // ─── List Invoices ──────────────────────────────────────────────────────────

    public Page<InvoiceDto> listInvoices(String billingModule, String paymentStatus,
                                          UUID patientId, LocalDate from, LocalDate to,
                                          int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (billingModule != null && !billingModule.isBlank()) {
                predicates.add(cb.equal(root.get("billingModule"), billingModule));
            }
            if (paymentStatus != null && !paymentStatus.isBlank()) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (patientId != null) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
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

        return invoiceRepository.findAll(spec, pageable).map(this::toDto);
    }

    // ─── Get Invoice by ID ──────────────────────────────────────────────────────

    public InvoiceDto getInvoiceById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        return toDto(invoice);
    }

    // ─── Create Invoice Manually ────────────────────────────────────────────────

    @Transactional
    public InvoiceDto createManualInvoice(InvoiceCreateRequest request, String createdBy) {
        log.info("Creating manual invoice for patient {} in module {}",
                request.getPatientId(), request.getBillingModule());

        Invoice invoice = buildInvoice(
                request.getPatientId(),
                request.getPatientName(),
                request.getBillingModule(),
                request.getSessionReferenceId(),
                request.getLineItems(),
                BigDecimal.ZERO,
                null,
                "manual",
                createdBy,
                request.getNotes()
        );

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Created invoice {} for patient {}", saved.getInvoiceNumber(), saved.getPatientId());
        return toDto(saved);
    }

    // ─── Create Invoice from Event ──────────────────────────────────────────────

    @Transactional
    public InvoiceDto createEventInvoice(UUID patientId, String patientName,
                                          String billingModule, UUID sessionReferenceId,
                                          List<LineItemRequest> lineItems,
                                          BigDecimal discountAmount, String discountReason) {
        // Idempotency: skip if already exists for this session
        if (sessionReferenceId != null) {
            invoiceRepository.findBySessionReferenceId(sessionReferenceId).ifPresent(existing -> {
                log.warn("Invoice already exists for session {}, skipping.", sessionReferenceId);
                throw new BusinessException("DUPLICATE_INVOICE",
                        "Invoice already exists for session: " + sessionReferenceId, 409);
            });
        }

        log.info("Creating event-driven invoice for patient {} (module: {})", patientId, billingModule);

        Invoice invoice = buildInvoice(
                patientId, patientName, billingModule, sessionReferenceId,
                lineItems, discountAmount, discountReason, "event", "system", null
        );

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Auto-created invoice {} from event", saved.getInvoiceNumber());
        return toDto(saved);
    }

    // ─── Record Payment ─────────────────────────────────────────────────────────

    @Transactional
    public PaymentResultDto recordPayment(UUID id, PaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        if ("paid".equals(invoice.getPaymentStatus())) {
            throw new BusinessException("ALREADY_PAID", "Invoice is already fully paid", 422);
        }
        if ("waived".equals(invoice.getPaymentStatus())) {
            throw new BusinessException("ALREADY_WAIVED", "Invoice has been waived", 422);
        }

        BigDecimal amountPaid = request.getAmountPaid();
        BigDecimal total = invoice.getTotalAmount();

        if (amountPaid.compareTo(total) >= 0) {
            invoice.setPaymentStatus("paid");
        } else {
            invoice.setPaymentStatus("partial");
        }

        invoice.setPaymentMethod(request.getPaymentMethod());
        invoice.setPaidAt(ZonedDateTime.now());

        Invoice saved = invoiceRepository.save(invoice);

        BigDecimal change = amountPaid.subtract(total).max(BigDecimal.ZERO);
        log.info("Payment recorded for invoice {}. Status: {}, Change: {}",
                saved.getInvoiceNumber(), saved.getPaymentStatus(), change);

        return new PaymentResultDto(toDto(saved), change);
    }

    // ─── Apply Discount ─────────────────────────────────────────────────────────

    @Transactional
    public InvoiceDto applyDiscount(UUID id, DiscountRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        if ("paid".equals(invoice.getPaymentStatus())) {
            throw new BusinessException("ALREADY_PAID", "Cannot apply discount to a paid invoice", 422);
        }
        if ("waived".equals(invoice.getPaymentStatus())) {
            throw new BusinessException("ALREADY_WAIVED", "Cannot apply discount to a waived invoice", 422);
        }

        BigDecimal discountAmount;
        if ("percentage".equals(request.getDiscountType())) {
            BigDecimal pct = request.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discountAmount = invoice.getSubtotal().multiply(pct).setScale(2, RoundingMode.HALF_UP);
        } else {
            discountAmount = request.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        }

        if (discountAmount.compareTo(invoice.getSubtotal()) > 0) {
            throw new BusinessException("INVALID_DISCOUNT",
                    "Discount amount cannot exceed subtotal", 400);
        }

        invoice.setDiscountAmount(discountAmount);
        invoice.setDiscountReason(request.getReason());
        invoice.setTotalAmount(invoice.getSubtotal().subtract(discountAmount));

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Discount applied to invoice {}. New total: {}", saved.getInvoiceNumber(), saved.getTotalAmount());
        return toDto(saved);
    }

    // ─── Print Data ─────────────────────────────────────────────────────────────

    public PrintDataDto getPrintData(UUID id, String hospitalName) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        ZonedDateTime createdAt = invoice.getCreatedAt();

        return PrintDataDto.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .hospitalName(hospitalName)
                .patientName(invoice.getPatientName())
                .patientNumber(invoice.getPatientId().toString())
                .date(createdAt != null ? createdAt.toLocalDate().toString() : "")
                .time(createdAt != null ? createdAt.toLocalTime().toString() : "")
                .lineItems(invoice.getLineItems().stream().map(this::toLineItemDto).toList())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .totalAmount(invoice.getTotalAmount())
                .paymentMethod(invoice.getPaymentMethod())
                .paidAt(invoice.getPaidAt())
                .cashierName(invoice.getCreatedBy())
                .build();
    }

    // ─── Helper: Build Invoice ───────────────────────────────────────────────────

    private Invoice buildInvoice(UUID patientId, String patientName,
                                  String billingModule, UUID sessionReferenceId,
                                  List<LineItemRequest> lineItemRequests,
                                  BigDecimal discountAmount, String discountReason,
                                  String source, String createdBy, String notes) {
        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .patientId(patientId)
                .patientName(patientName)
                .billingModule(billingModule)
                .sessionReferenceId(sessionReferenceId)
                .discountAmount(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                .discountReason(discountReason)
                .paymentStatus("pending")
                .source(source)
                .createdBy(createdBy)
                .notes(notes)
                .build();

        // Build line items and calculate subtotal
        List<LineItem> lineItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (LineItemRequest req : lineItemRequests) {
            BigDecimal total = req.getUnitPrice()
                    .multiply(BigDecimal.valueOf(req.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            LineItem item = LineItem.builder()
                    .invoice(invoice)
                    .itemName(req.getItemName())
                    .itemType(req.getItemType())
                    .quantity(req.getQuantity())
                    .unitPrice(req.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                    .totalPrice(total)
                    .build();

            lineItems.add(item);
            subtotal = subtotal.add(total);
        }

        invoice.setLineItems(lineItems);
        invoice.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));

        BigDecimal effectiveDiscount = invoice.getDiscountAmount();
        invoice.setTotalAmount(subtotal.subtract(effectiveDiscount).setScale(2, RoundingMode.HALF_UP));

        return invoice;
    }

    // ─── Mapper ──────────────────────────────────────────────────────────────────

    public InvoiceDto toDto(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .patientId(invoice.getPatientId())
                .patientName(invoice.getPatientName())
                .billingModule(invoice.getBillingModule())
                .sessionReferenceId(invoice.getSessionReferenceId())
                .lineItems(invoice.getLineItems().stream().map(this::toLineItemDto).toList())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .discountReason(invoice.getDiscountReason())
                .totalAmount(invoice.getTotalAmount())
                .paymentStatus(invoice.getPaymentStatus())
                .paymentMethod(invoice.getPaymentMethod())
                .source(invoice.getSource())
                .paidAt(invoice.getPaidAt())
                .createdBy(invoice.getCreatedBy())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private LineItemDto toLineItemDto(LineItem item) {
        return LineItemDto.builder()
                .id(item.getId())
                .invoiceId(item.getInvoice() != null ? item.getInvoice().getId() : null)
                .itemName(item.getItemName())
                .itemType(item.getItemType())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
