package com.hms.finance.service;

import com.hms.finance.dto.*;
import com.hms.finance.entity.Invoice;
import com.hms.finance.entity.LineItem;
import com.hms.finance.exception.BusinessException;
import com.hms.finance.exception.ResourceNotFoundException;
import com.hms.finance.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private UUID patientId;
    private Invoice savedInvoice;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();

        LineItem lineItem = LineItem.builder()
                .id(UUID.randomUUID())
                .itemName("Doctor Consultation")
                .itemType("consultation")
                .quantity(1)
                .unitPrice(new BigDecimal("1500.00"))
                .totalPrice(new BigDecimal("1500.00"))
                .build();

        savedInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2026-00001")
                .patientId(patientId)
                .patientName("Andrea Lalema")
                .billingModule("opd")
                .subtotal(new BigDecimal("1500.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1500.00"))
                .paymentStatus("pending")
                .source("manual")
                .createdBy("receptionist")
                .createdAt(ZonedDateTime.now())
                .lineItems(new ArrayList<>())
                .build();
        lineItem.setInvoice(savedInvoice);
        savedInvoice.getLineItems().add(lineItem);
    }

    // ─── createManualInvoice ─────────────────────────────────────────────────────

    @Test
    void createManualInvoice_withValidRequest_savesAndReturnsDto() {
        when(invoiceRepository.findMaxSequenceForYear(any())).thenReturn(0);
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setPatientId(patientId);
        request.setPatientName("Andrea Lalema");
        request.setBillingModule("opd");

        LineItemRequest lineItemReq = new LineItemRequest();
        lineItemReq.setItemName("Doctor Consultation");
        lineItemReq.setItemType("consultation");
        lineItemReq.setQuantity(1);
        lineItemReq.setUnitPrice(new BigDecimal("1500.00"));
        request.setLineItems(List.of(lineItemReq));

        InvoiceDto result = invoiceService.createManualInvoice(request, "receptionist");

        assertThat(result).isNotNull();
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2026-00001");
        assertThat(result.getPaymentStatus()).isEqualTo("pending");
        assertThat(result.getSource()).isEqualTo("manual");
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void createManualInvoice_calculatesSubtotalCorrectly() {
        when(invoiceRepository.findMaxSequenceForYear(any())).thenReturn(5);

        LineItem item1 = LineItem.builder()
                .id(UUID.randomUUID())
                .itemName("Consultation")
                .itemType("consultation")
                .quantity(1)
                .unitPrice(new BigDecimal("1500.00"))
                .totalPrice(new BigDecimal("1500.00"))
                .build();
        LineItem item2 = LineItem.builder()
                .id(UUID.randomUUID())
                .itemName("ECG")
                .itemType("procedure")
                .quantity(1)
                .unitPrice(new BigDecimal("500.00"))
                .totalPrice(new BigDecimal("500.00"))
                .build();

        Invoice multiItemInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2026-00006")
                .patientId(patientId)
                .patientName("John Doe")
                .billingModule("opd")
                .subtotal(new BigDecimal("2000.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentStatus("pending")
                .source("manual")
                .createdBy("receptionist")
                .lineItems(List.of(item1, item2))
                .build();

        when(invoiceRepository.save(any(Invoice.class))).thenReturn(multiItemInvoice);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setPatientId(patientId);
        request.setPatientName("John Doe");
        request.setBillingModule("opd");

        LineItemRequest r1 = new LineItemRequest();
        r1.setItemName("Consultation");
        r1.setItemType("consultation");
        r1.setQuantity(1);
        r1.setUnitPrice(new BigDecimal("1500.00"));

        LineItemRequest r2 = new LineItemRequest();
        r2.setItemName("ECG");
        r2.setItemType("procedure");
        r2.setQuantity(1);
        r2.setUnitPrice(new BigDecimal("500.00"));

        request.setLineItems(List.of(r1, r2));

        InvoiceDto result = invoiceService.createManualInvoice(request, "receptionist");

        assertThat(result.getSubtotal()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    // ─── getInvoiceById ──────────────────────────────────────────────────────────

    @Test
    void getInvoiceById_whenFound_returnsDto() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));

        InvoiceDto result = invoiceService.getInvoiceById(savedInvoice.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(savedInvoice.getId());
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-2026-00001");
    }

    @Test
    void getInvoiceById_whenNotFound_throwsResourceNotFoundException() {
        UUID randomId = UUID.randomUUID();
        when(invoiceRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoiceById(randomId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Invoice not found");
    }

    // ─── recordPayment ───────────────────────────────────────────────────────────

    @Test
    void recordPayment_withFullPayment_setsStatusToPaid() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("cash");
        request.setAmountPaid(new BigDecimal("1500.00"));

        PaymentResultDto result = invoiceService.recordPayment(savedInvoice.getId(), request);

        assertThat(result.getInvoice().getPaymentStatus()).isEqualTo("paid");
        assertThat(result.getInvoice().getPaymentMethod()).isEqualTo("cash");
        assertThat(result.getChangeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordPayment_withOverpayment_returnsChange() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("cash");
        request.setAmountPaid(new BigDecimal("2000.00"));

        PaymentResultDto result = invoiceService.recordPayment(savedInvoice.getId(), request);

        assertThat(result.getInvoice().getPaymentStatus()).isEqualTo("paid");
        assertThat(result.getChangeAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void recordPayment_withPartialPayment_setsStatusToPartial() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("cash");
        request.setAmountPaid(new BigDecimal("1000.00"));

        PaymentResultDto result = invoiceService.recordPayment(savedInvoice.getId(), request);

        assertThat(result.getInvoice().getPaymentStatus()).isEqualTo("partial");
    }

    @Test
    void recordPayment_onAlreadyPaidInvoice_throwsBusinessException() {
        savedInvoice.setPaymentStatus("paid");
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("cash");
        request.setAmountPaid(new BigDecimal("1500.00"));

        assertThatThrownBy(() -> invoiceService.recordPayment(savedInvoice.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already fully paid");
    }

    // ─── applyDiscount ───────────────────────────────────────────────────────────

    @Test
    void applyDiscount_withPercentage_recalculatesTotal() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        DiscountRequest request = new DiscountRequest();
        request.setDiscountType("percentage");
        request.setDiscountValue(new BigDecimal("10"));
        request.setReason("Staff discount");

        InvoiceDto result = invoiceService.applyDiscount(savedInvoice.getId(), request);

        assertThat(result.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1350.00"));
        assertThat(result.getDiscountReason()).isEqualTo("Staff discount");
    }

    @Test
    void applyDiscount_withFlatAmount_recalculatesTotal() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        DiscountRequest request = new DiscountRequest();
        request.setDiscountType("flat");
        request.setDiscountValue(new BigDecimal("200.00"));
        request.setReason("Senior citizen discount");

        InvoiceDto result = invoiceService.applyDiscount(savedInvoice.getId(), request);

        assertThat(result.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1300.00"));
    }

    @Test
    void applyDiscount_onPaidInvoice_throwsBusinessException() {
        savedInvoice.setPaymentStatus("paid");
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));

        DiscountRequest request = new DiscountRequest();
        request.setDiscountType("flat");
        request.setDiscountValue(new BigDecimal("100.00"));
        request.setReason("Goodwill");

        assertThatThrownBy(() -> invoiceService.applyDiscount(savedInvoice.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot apply discount to a paid invoice");
    }

    @Test
    void applyDiscount_exceedingSubtotal_throwsBusinessException() {
        when(invoiceRepository.findById(savedInvoice.getId())).thenReturn(Optional.of(savedInvoice));

        DiscountRequest request = new DiscountRequest();
        request.setDiscountType("flat");
        request.setDiscountValue(new BigDecimal("2000.00")); // Exceeds subtotal of 1500
        request.setReason("Test");

        assertThatThrownBy(() -> invoiceService.applyDiscount(savedInvoice.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Discount amount cannot exceed subtotal");
    }

    // ─── generateInvoiceNumber ───────────────────────────────────────────────────

    @Test
    void generateInvoiceNumber_withNoExisting_returnsFirstNumber() {
        when(invoiceRepository.findMaxSequenceForYear(any())).thenReturn(0);

        String invoiceNumber = invoiceService.generateInvoiceNumber();

        assertThat(invoiceNumber).matches("INV-\\d{4}-00001");
    }

    @Test
    void generateInvoiceNumber_withExisting_incrementsSequence() {
        when(invoiceRepository.findMaxSequenceForYear(any())).thenReturn(42);

        String invoiceNumber = invoiceService.generateInvoiceNumber();

        assertThat(invoiceNumber).matches("INV-\\d{4}-00043");
    }

    // ─── createEventInvoice (idempotency) ────────────────────────────────────────

    @Test
    void createEventInvoice_withDuplicateSession_throwsBusinessException() {
        UUID sessionId = UUID.randomUUID();
        when(invoiceRepository.findBySessionReferenceId(sessionId))
                .thenReturn(Optional.of(savedInvoice));

        LineItemRequest item = new LineItemRequest();
        item.setItemName("Doctor Consultation");
        item.setItemType("consultation");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("1500.00"));

        assertThatThrownBy(() -> invoiceService.createEventInvoice(
                patientId, "Andrea", "opd", sessionId, List.of(item), BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_INVOICE");
    }
}
