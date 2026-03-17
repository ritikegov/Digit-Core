package org.egov.pg.validator;

import org.egov.pg.config.AppProperties;
import org.egov.pg.models.TaxAndPayment;
import org.egov.pg.models.Transaction;
import org.egov.pg.models.User;
import org.egov.pg.repository.TransactionRepository;
import org.egov.pg.service.GatewayService;
import org.egov.pg.web.models.TransactionCriteria;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionValidatorTest {

    @Mock
    private GatewayService gatewayService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AppProperties appProperties;

    private TransactionValidator transactionValidator;

    @BeforeEach
    void setUp() {
        transactionValidator = new TransactionValidator(gatewayService, transactionRepository, appProperties);
    }

    @Test
    void validateCreateTxn_validTransaction_passesValidation() {
        Transaction transaction = baseTransaction("100", Transaction.TxnStatusEnum.PENDING);
        when(gatewayService.isGatewayActive("PAYTM")).thenReturn(true);
        when(transactionRepository.fetchTransactions(any(TransactionCriteria.class))).thenReturn(Collections.emptyList());

        transactionValidator.validateCreateTxn(transaction);

        verify(gatewayService).isGatewayActive("PAYTM");
    }

    @Test
    void validateCreateTxn_inactiveGatewayAndAmountMismatch_throwsCombinedErrors() {
        Transaction transaction = baseTransaction("100", Transaction.TxnStatusEnum.PENDING);
        transaction.setTaxAndPayments(Collections.singletonList(TaxAndPayment.builder()
                .billId("B1")
                .amountPaid(new BigDecimal("90"))
                .taxAmount(new BigDecimal("90"))
                .build()));

        when(gatewayService.isGatewayActive("PAYTM")).thenReturn(false);
        when(transactionRepository.fetchTransactions(any(TransactionCriteria.class))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> transactionValidator.validateCreateTxn(transaction))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("INVALID_PAYMENT_GATEWAY")
                .hasMessageContaining("TXN_CREATE_INVALID_TXN_AMT");
    }

    @Test
    void validateCreateTxn_pendingOrSuccessfulBillTransactions_throwsRelevantErrors() {
        Transaction transaction = baseTransaction("100", Transaction.TxnStatusEnum.PENDING);
        Transaction existingPending = baseTransaction("100", Transaction.TxnStatusEnum.PENDING);
        Transaction existingSuccess = baseTransaction("100", Transaction.TxnStatusEnum.SUCCESS);

        when(gatewayService.isGatewayActive("PAYTM")).thenReturn(true);
        when(appProperties.getEarlyReconcileJobRunInterval()).thenReturn(15);
        when(transactionRepository.fetchTransactions(any(TransactionCriteria.class)))
                .thenReturn(List.of(existingPending, existingSuccess));

        assertThatThrownBy(() -> transactionValidator.validateCreateTxn(transaction))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("TXN_ABRUPTLY_DISCARDED")
                .hasMessageContaining("TXN_CREATE_BILL_ALREADY_PAID");
    }

    @Test
    void validateUpdateTxn_missingTransactionId_throwsException() {
        when(gatewayService.getTxnId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionValidator.validateUpdateTxn(Map.of("invalid", "1")))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getCode()).isEqualTo("MISSING_UPDATE_TXN_ID");
                    assertThat(ce.getMessage()).contains("Cannot process request, missing transaction id");
                });
    }

    @Test
    void validateUpdateTxn_transactionNotFound_throwsException() {
        when(gatewayService.getTxnId(any())).thenReturn(Optional.of("TXN-10"));
        when(transactionRepository.fetchTransactions(any(TransactionCriteria.class))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> transactionValidator.validateUpdateTxn(Map.of("transactionId", "TXN-10")))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getCode()).isEqualTo("TXN_UPDATE_NOT_FOUND");
                    assertThat(ce.getMessage()).contains("Transaction not found");
                });
    }

    @Test
    void validateUpdateTxn_transactionExists_returnsFirstResultAndBuildsCriteriaWithTxnId() {
        Transaction existing = baseTransaction("50", Transaction.TxnStatusEnum.PENDING);
        existing.setTxnId("TXN-11");
        when(gatewayService.getTxnId(any())).thenReturn(Optional.of("TXN-11"));
        when(transactionRepository.fetchTransactions(any(TransactionCriteria.class))).thenReturn(List.of(existing));

        Transaction result = transactionValidator.validateUpdateTxn(Map.of("transactionId", "TXN-11"));

        assertThat(result).isSameAs(existing);
        ArgumentCaptor<TransactionCriteria> criteriaCaptor = ArgumentCaptor.forClass(TransactionCriteria.class);
        verify(transactionRepository).fetchTransactions(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().getTxnId()).isEqualTo("TXN-11");
    }

    private Transaction baseTransaction(String amount, Transaction.TxnStatusEnum status) {
        return Transaction.builder()
                .tenantId("pb")
                .txnAmount(amount)
                .billId("B1")
                .module("PT")
                .consumerCode("PT-001")
                .taxAndPayments(Collections.singletonList(TaxAndPayment.builder()
                        .billId("B1")
                        .amountPaid(new BigDecimal(amount))
                        .taxAmount(new BigDecimal(amount))
                        .build()))
                .productInfo("Property Tax")
                .gateway("PAYTM")
                .callbackUrl("https://callback")
                .user(User.builder().tenantId("pb").name("User").mobileNumber("9999999999").uuid("u1").userName("u").build())
                .txnStatus(status)
                .build();
    }
}