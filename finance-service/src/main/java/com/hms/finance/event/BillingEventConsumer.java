package com.hms.finance.event;

import com.hms.finance.config.RabbitMQConfig;
import com.hms.finance.dto.LineItemRequest;
import com.hms.finance.exception.BusinessException;
import com.hms.finance.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventConsumer {

    private final InvoiceService invoiceService;

    // ─── OPD Billing ─────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.BILLING_OPD_QUEUE)
    public void handleBillingOpd(BillingOpdEvent event) {
        log.info("Received billing.opd event for session: {}", event.getSessionId());
        try {
            List<LineItemRequest> lineItems = new ArrayList<>();
            LineItemRequest item = new LineItemRequest();
            item.setItemName("Doctor Consultation");
            item.setItemType("consultation");
            item.setQuantity(1);
            item.setUnitPrice(BigDecimal.valueOf(
                    event.getConsultationFee() != null ? event.getConsultationFee() : 0.0));
            lineItems.add(item);

            BigDecimal discountAmount = calculateDiscount(
                    item.getUnitPrice(), event.getDiscountPercent());

            invoiceService.createEventInvoice(
                    event.getPatientId(),
                    event.getPatientName(),
                    "opd",
                    event.getSessionId(),
                    lineItems,
                    discountAmount,
                    event.getDiscountPercent() != null ? "Session discount" : null
            );
        } catch (BusinessException e) {
            if ("DUPLICATE_INVOICE".equals(e.getCode())) {
                log.warn("Duplicate OPD invoice for session {}, ignoring.", event.getSessionId());
            } else {
                log.error("Error processing billing.opd event for session {}: {}",
                        event.getSessionId(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing billing.opd event for session {}",
                    event.getSessionId(), e);
        }
    }

    // ─── Wound Care Billing ───────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.BILLING_WOUND_QUEUE)
    public void handleBillingWound(BillingWoundEvent event) {
        log.info("Received billing.wound event for session: {}", event.getSessionId());
        try {
            List<LineItemRequest> lineItems = new ArrayList<>();
            LineItemRequest item = new LineItemRequest();
            item.setItemName("Wound Care Treatment");
            item.setItemType("wound_care");
            item.setQuantity(1);
            item.setUnitPrice(BigDecimal.valueOf(
                    event.getWoundCareFee() != null ? event.getWoundCareFee() : 0.0));
            lineItems.add(item);

            BigDecimal discountAmount = calculateDiscount(
                    item.getUnitPrice(), event.getDiscountPercent());

            invoiceService.createEventInvoice(
                    event.getPatientId(),
                    event.getPatientName(),
                    "wound_care",
                    event.getSessionId(),
                    lineItems,
                    discountAmount,
                    event.getDiscountPercent() != null ? "Session discount" : null
            );
        } catch (BusinessException e) {
            if ("DUPLICATE_INVOICE".equals(e.getCode())) {
                log.warn("Duplicate wound care invoice for session {}, ignoring.", event.getSessionId());
            } else {
                log.error("Error processing billing.wound event: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing billing.wound event for session {}",
                    event.getSessionId(), e);
        }
    }

    // ─── Ward Billing ─────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.BILLING_WARD_QUEUE)
    public void handleBillingWard(BillingWardEvent event) {
        log.info("Received billing.ward event for admission: {}", event.getAdmissionId());
        try {
            List<LineItemRequest> lineItems = new ArrayList<>();

            if (event.getServices() != null && !event.getServices().isEmpty()) {
                for (BillingWardEvent.WardServiceItem svc : event.getServices()) {
                    LineItemRequest item = new LineItemRequest();
                    item.setItemName(svc.getName());
                    item.setItemType(svc.getType() != null ? svc.getType() : "other");
                    item.setQuantity(svc.getQuantity() != null ? svc.getQuantity() : 1);
                    item.setUnitPrice(BigDecimal.valueOf(
                            svc.getUnitPrice() != null ? svc.getUnitPrice() : 0.0));
                    lineItems.add(item);
                }
            } else {
                // Fallback: single line item with total
                LineItemRequest item = new LineItemRequest();
                item.setItemName("Ward Services");
                item.setItemType("bed_charge");
                item.setQuantity(1);
                item.setUnitPrice(BigDecimal.valueOf(
                        event.getTotalAmount() != null ? event.getTotalAmount() : 0.0));
                lineItems.add(item);
            }

            invoiceService.createEventInvoice(
                    event.getPatientId(),
                    event.getPatientName(),
                    "ward",
                    event.getAdmissionId(),
                    lineItems,
                    BigDecimal.ZERO,
                    null
            );
        } catch (BusinessException e) {
            if ("DUPLICATE_INVOICE".equals(e.getCode())) {
                log.warn("Duplicate ward invoice for admission {}, ignoring.", event.getAdmissionId());
            } else {
                log.error("Error processing billing.ward event: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing billing.ward event for admission {}",
                    event.getAdmissionId(), e);
        }
    }

    // ─── Lab Billing ──────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.BILLING_LAB_QUEUE)
    public void handleBillingLab(BillingLabEvent event) {
        log.info("Received billing.lab event for order: {}", event.getOrderId());
        try {
            List<LineItemRequest> lineItems = new ArrayList<>();

            if (event.getTests() != null && !event.getTests().isEmpty()) {
                for (BillingLabEvent.LabTestItem test : event.getTests()) {
                    LineItemRequest item = new LineItemRequest();
                    item.setItemName(test.getTestName());
                    item.setItemType("lab_test");
                    item.setQuantity(1);
                    item.setUnitPrice(BigDecimal.valueOf(
                            test.getPrice() != null ? test.getPrice() : 0.0));
                    lineItems.add(item);
                }
            } else {
                LineItemRequest item = new LineItemRequest();
                item.setItemName("Lab Tests");
                item.setItemType("lab_test");
                item.setQuantity(1);
                item.setUnitPrice(BigDecimal.valueOf(
                        event.getTotalAmount() != null ? event.getTotalAmount() : 0.0));
                lineItems.add(item);
            }

            invoiceService.createEventInvoice(
                    event.getPatientId(),
                    event.getPatientName(),
                    "lab",
                    event.getOrderId(),
                    lineItems,
                    BigDecimal.ZERO,
                    null
            );
        } catch (BusinessException e) {
            if ("DUPLICATE_INVOICE".equals(e.getCode())) {
                log.warn("Duplicate lab invoice for order {}, ignoring.", event.getOrderId());
            } else {
                log.error("Error processing billing.lab event: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing billing.lab event for order {}",
                    event.getOrderId(), e);
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────────

    private BigDecimal calculateDiscount(BigDecimal amount, Double discountPercent) {
        if (discountPercent == null || discountPercent <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(discountPercent / 100.0))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
