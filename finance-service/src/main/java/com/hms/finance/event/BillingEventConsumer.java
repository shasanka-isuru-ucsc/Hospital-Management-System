package com.hms.finance.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.finance.config.NatsConfig;
import com.hms.finance.dto.InvoiceDto;
import com.hms.finance.dto.LineItemRequest;
import com.hms.finance.dto.PaymentRequest;
import com.hms.finance.exception.BusinessException;
import com.hms.finance.service.InvoiceService;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventConsumer {

    private final Connection natsConnection;
    private final JetStream jetStream;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    private Dispatcher dispatcher;

    @PostConstruct
    public void subscribe() {
        try {
            dispatcher = natsConnection.createDispatcher();

            jetStream.subscribe(NatsConfig.SUBJECT_BILLING_OPD, dispatcher,
                    msg -> handleMessage(msg, BillingOpdEvent.class, this::handleBillingOpd),
                    false,
                    PushSubscribeOptions.builder().durable("finance-billing-opd")
                            .configuration(ConsumerConfiguration.builder()
                                    .filterSubject(NatsConfig.SUBJECT_BILLING_OPD).build()).build());

            jetStream.subscribe(NatsConfig.SUBJECT_BILLING_WOUND, dispatcher,
                    msg -> handleMessage(msg, BillingWoundEvent.class, this::handleBillingWound),
                    false,
                    PushSubscribeOptions.builder().durable("finance-billing-wound")
                            .configuration(ConsumerConfiguration.builder()
                                    .filterSubject(NatsConfig.SUBJECT_BILLING_WOUND).build()).build());

            jetStream.subscribe(NatsConfig.SUBJECT_BILLING_WARD, dispatcher,
                    msg -> handleMessage(msg, BillingWardEvent.class, this::handleBillingWard),
                    false,
                    PushSubscribeOptions.builder().durable("finance-billing-ward")
                            .configuration(ConsumerConfiguration.builder()
                                    .filterSubject(NatsConfig.SUBJECT_BILLING_WARD).build()).build());

            jetStream.subscribe(NatsConfig.SUBJECT_BILLING_LAB, dispatcher,
                    msg -> handleMessage(msg, BillingLabEvent.class, this::handleBillingLab),
                    false,
                    PushSubscribeOptions.builder().durable("finance-billing-lab")
                            .configuration(ConsumerConfiguration.builder()
                                    .filterSubject(NatsConfig.SUBJECT_BILLING_LAB).build()).build());

            log.info("Finance service subscribed to all billing NATS subjects");
        } catch (Exception e) {
            log.error("Failed to subscribe to NATS billing subjects", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (dispatcher != null) {
            natsConnection.closeDispatcher(dispatcher);
        }
    }

    private <T> void handleMessage(Message msg, Class<T> type, Consumer<T> handler) {
        try {
            T event = objectMapper.readValue(msg.getData(), type);
            handler.accept(event);
            msg.ack();
        } catch (Exception e) {
            log.error("Error processing NATS message on subject {}", msg.getSubject(), e);
        }
    }

    // ─── OPD Billing ─────────────────────────────────────────────────────────────

    private void handleBillingOpd(BillingOpdEvent event) {
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
                    event.getDiscountReason()
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

    private void handleBillingWound(BillingWoundEvent event) {
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
                    event.getDiscountReason()
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

    private void handleBillingWard(BillingWardEvent event) {
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

    private void handleBillingLab(BillingLabEvent event) {
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

            InvoiceDto invoice = invoiceService.createEventInvoice(
                    event.getPatientId(),
                    event.getPatientName(),
                    "lab",
                    event.getOrderId(),
                    lineItems,
                    BigDecimal.ZERO,
                    null
            );
            if (event.getPaymentMethod() != null && event.getTotalAmount() != null && event.getTotalAmount() > 0) {
                PaymentRequest paymentRequest = new PaymentRequest();
                paymentRequest.setPaymentMethod(event.getPaymentMethod());
                paymentRequest.setAmountPaid(BigDecimal.valueOf(event.getTotalAmount()));
                invoiceService.recordPayment(invoice.getId(), paymentRequest);
                log.info("Lab invoice {} marked as paid via {}", invoice.getId(), event.getPaymentMethod());
            }
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
