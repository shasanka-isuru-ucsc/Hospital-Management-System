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
class LabOrderServiceTest {

    @Mock
    private LabOrderRepository labOrderRepository;

    @Mock
    private LabTestRepository labTestRepository;

    @Mock
    private OrderTestRepository orderTestRepository;

    @Mock
    private MinioService minioService;

    @Mock
    private BillingLabEventPublisher billingLabEventPublisher;

    @InjectMocks
    private LabOrderService labOrderService;

    private UUID orderId;
    private UUID testId;
    private LabTest labTest;
    private LabOrder savedOrder;
    private OrderTest orderTest;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        testId = UUID.randomUUID();

        labTest = LabTest.builder()
                .id(testId)
                .name("Complete Blood Count")
                .code("CBC")
                .unitPrice(new BigDecimal("750.00"))
                .isActive(true)
                .build();

        orderTest = OrderTest.builder()
                .id(UUID.randomUUID())
                .testId(testId)
                .testName("Complete Blood Count")
                .testCode("CBC")
                .urgency("routine")
                .unitPrice(new BigDecimal("750.00"))
                .status("pending")
                .build();

        savedOrder = LabOrder.builder()
                .id(orderId)
                .patientName("Andrea Lalema")
                .patientMobile("+94771234567")
                .source("walk_in")
                .status("registered")
                .paymentStatus("pending")
                .totalAmount(new BigDecimal("750.00"))
                .createdBy("lab_tech")
                .createdAt(ZonedDateTime.now())
                .tests(new ArrayList<>())
                .build();
        orderTest.setOrder(savedOrder);
        savedOrder.getTests().add(orderTest);
    }

    // ─── createOrder ──────────────────────────────────────────────────────────────

    @Test
    void createOrder_withWalkIn_savesAndReturnsDto() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.of(labTest));
        when(labOrderRepository.save(any(LabOrder.class))).thenReturn(savedOrder);

        LabOrderCreateRequest request = new LabOrderCreateRequest();
        request.setPatientName("Andrea Lalema");
        request.setPatientMobile("+94771234567");
        request.setSource("walk_in");
        OrderTestRequest testReq = new OrderTestRequest();
        testReq.setTestId(testId);
        testReq.setUrgency("routine");
        request.setTests(List.of(testReq));

        LabOrderDto result = labOrderService.createOrder(request, "lab_tech");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("registered");
        assertThat(result.getPaymentStatus()).isEqualTo("pending");
        assertThat(result.getSource()).isEqualTo("walk_in");
        verify(labOrderRepository).save(any(LabOrder.class));
    }

    @Test
    void createOrder_withMissingPatientInfo_throwsBusinessException() {
        LabOrderCreateRequest request = new LabOrderCreateRequest();
        request.setSource("walk_in");
        OrderTestRequest testReq = new OrderTestRequest();
        testReq.setTestId(testId);
        request.setTests(List.of(testReq));
        // No patientId or patientName

        assertThatThrownBy(() -> labOrderService.createOrder(request, "lab_tech"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSING_PATIENT_INFO");
    }

    @Test
    void createOrder_withInvalidTestId_throwsBusinessException() {
        when(labTestRepository.findById(testId)).thenReturn(Optional.empty());

        LabOrderCreateRequest request = new LabOrderCreateRequest();
        request.setPatientName("Andrea Lalema");
        request.setSource("walk_in");
        OrderTestRequest testReq = new OrderTestRequest();
        testReq.setTestId(testId);
        request.setTests(List.of(testReq));

        assertThatThrownBy(() -> labOrderService.createOrder(request, "lab_tech"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_TEST_ID");
    }

    @Test
    void createOrder_withInactiveTest_throwsBusinessException() {
        labTest.setIsActive(false);
        when(labTestRepository.findById(testId)).thenReturn(Optional.of(labTest));

        LabOrderCreateRequest request = new LabOrderCreateRequest();
        request.setPatientName("Andrea Lalema");
        request.setSource("walk_in");
        OrderTestRequest testReq = new OrderTestRequest();
        testReq.setTestId(testId);
        request.setTests(List.of(testReq));

        assertThatThrownBy(() -> labOrderService.createOrder(request, "lab_tech"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INACTIVE_TEST");
    }

    @Test
    void createOrder_duplicateClinicalSessionId_throwsBusinessException() {
        UUID sessionId = UUID.randomUUID();
        when(labOrderRepository.findBySessionId(sessionId)).thenReturn(Optional.of(savedOrder));

        LabOrderCreateRequest request = new LabOrderCreateRequest();
        request.setPatientName("Andrea Lalema");
        request.setSource("clinical_request");
        request.setSessionId(sessionId);
        OrderTestRequest testReq = new OrderTestRequest();
        testReq.setTestId(testId);
        request.setTests(List.of(testReq));

        assertThatThrownBy(() -> labOrderService.createOrder(request, "system"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_ORDER");
    }

    // ─── getOrderById ────────────────────────────────────────────────────────────

    @Test
    void getOrderById_whenFound_returnsDto() {
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        LabOrderDto result = labOrderService.getOrderById(orderId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getPatientName()).isEqualTo("Andrea Lalema");
    }

    @Test
    void getOrderById_whenNotFound_throwsResourceNotFoundException() {
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labOrderService.getOrderById(orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    // ─── enterResults ─────────────────────────────────────────────────────────────

    @Test
    void enterResults_setsOrderToProcessingOnPartialResults() {
        UUID orderTestId = orderTest.getId();
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderTestRepository.findById(orderTestId)).thenReturn(Optional.of(orderTest));
        when(orderTestRepository.save(any(OrderTest.class))).thenReturn(orderTest);

        // Add a second pending test
        OrderTest secondTest = OrderTest.builder()
                .id(UUID.randomUUID())
                .order(savedOrder)
                .testName("Widal Test")
                .status("pending")
                .build();
        savedOrder.getTests().add(secondTest);

        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ResultItem resultItem = new ResultItem();
        resultItem.setOrderTestId(orderTestId);
        resultItem.setResultValue("WBC: 9.2 x10³/μL");
        resultItem.setIsAbnormal(false);

        ResultsUpdateRequest request = new ResultsUpdateRequest();
        request.setResults(List.of(resultItem));

        // Mark first test as completed via mock
        orderTest.setStatus("completed");

        LabOrderDto result = labOrderService.enterResults(orderId, request);

        assertThat(result).isNotNull();
        // Only one test completed, one still pending → status stays processing
        assertThat(result.getStatus()).isEqualTo("processing");
    }

    @Test
    void enterResults_setsOrderToCompletedWhenAllDone() {
        UUID orderTestId = orderTest.getId();
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderTestRepository.findById(orderTestId)).thenReturn(Optional.of(orderTest));
        when(orderTestRepository.save(any(OrderTest.class))).thenReturn(orderTest);
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mark the single test as completed by the mock
        orderTest.setStatus("completed");

        ResultItem resultItem = new ResultItem();
        resultItem.setOrderTestId(orderTestId);
        resultItem.setResultValue("WBC: 9.2 x10³/μL");

        ResultsUpdateRequest request = new ResultsUpdateRequest();
        request.setResults(List.of(resultItem));

        LabOrderDto result = labOrderService.enterResults(orderId, request);

        assertThat(result.getStatus()).isEqualTo("completed");
    }

    @Test
    void enterResults_onCancelledOrder_throwsBusinessException() {
        savedOrder.setStatus("cancelled");
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        ResultsUpdateRequest request = new ResultsUpdateRequest();
        request.setResults(List.of(new ResultItem()));

        assertThatThrownBy(() -> labOrderService.enterResults(orderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_CANCELLED");
    }

    @Test
    void enterResults_onCompletedOrder_throwsBusinessException() {
        savedOrder.setStatus("completed");
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        ResultsUpdateRequest request = new ResultsUpdateRequest();
        request.setResults(List.of(new ResultItem()));

        assertThatThrownBy(() -> labOrderService.enterResults(orderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_COMPLETED");
    }

    // ─── recordPayment ────────────────────────────────────────────────────────────

    @Test
    void recordPayment_setsStatusToPaidAndPublishesEvent() {
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(billingLabEventPublisher).publishBillingLab(any(LabOrder.class));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("cash");
        request.setAmountPaid(new BigDecimal("750.00"));

        LabOrderDto result = labOrderService.recordPayment(orderId, request);

        assertThat(result.getPaymentStatus()).isEqualTo("paid");
        verify(billingLabEventPublisher).publishBillingLab(any(LabOrder.class));
    }

    @Test
    void recordPayment_onAlreadyPaidOrder_throwsBusinessException() {
        savedOrder.setPaymentStatus("paid");
        when(labOrderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("cash");
        request.setAmountPaid(new BigDecimal("750.00"));

        assertThatThrownBy(() -> labOrderService.recordPayment(orderId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ALREADY_PAID");
    }
}
