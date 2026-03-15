package org.egov.pg.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.pg.config.AppProperties;
import org.egov.pg.models.TaxAndPayment;
import org.egov.pg.models.Transaction;
import org.egov.pg.repository.TransactionRepository;
import org.egov.pg.service.GatewayService;
import org.egov.pg.web.models.TransactionCriteria;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class TransactionValidator {

	private final GatewayService gatewayService;
	private final TransactionRepository transactionRepository;
	private final AppProperties appProperties;


	@Autowired
	public TransactionValidator(GatewayService gatewayService, TransactionRepository transactionRepository,
								AppProperties appProperties) {
		this.gatewayService = gatewayService;
		this.transactionRepository = transactionRepository;
		this.appProperties = appProperties;
	}

	/**
	 * Validate the transaction,
	 * Check if gateway is available and active
	 * Check if module specific order id is unique
	 *
	 * @param txn Transaction object to be validated
	 */
	public void validateCreateTxn(Transaction txn) {
		Map<String, String> errorMap = new HashMap<>();
		isGatewayActive(txn, errorMap);
		validateIfTxnExistsForBill(txn, errorMap);
		validateTxnAmount(txn, errorMap);

		if (!errorMap.isEmpty())
			throw new CustomException(errorMap);
	}

	/**
	 * Validate update of transaction
	 * Check if transaction id exists in query params provided
	 * Check if transaction id exists in system
	 *
	 * @param requestParams
	 * @return
	 */
	public Transaction validateUpdateTxn(Map<String, String> requestParams) {

		Optional<String> optional = gatewayService.getTxnId(requestParams);

		if (!optional.isPresent())
			throw new CustomException("MISSING_UPDATE_TXN_ID", "Cannot process request, missing transaction id");

		TransactionCriteria criteria = TransactionCriteria.builder()
				.txnId(optional.get())
				.build();

		List<Transaction> statuses = transactionRepository.fetchTransactions(criteria);

		//TODO Add to error queue
		if (statuses.isEmpty()) {
			throw new CustomException("TXN_UPDATE_NOT_FOUND", "Transaction not found");
		}

		return statuses.get(0);
	}


	/**
	 * Validations if transaction(s) already exists for this bill
	 * No transaction should exist in success / pending state for this bill     *
	 *
	 * @param txn      Transaction for which validation should happen
	 * @param errorMap Map of errors occurred during validations
	 */
	private void validateIfTxnExistsForBill(Transaction txn, Map<String, String> errorMap) {
		TransactionCriteria criteria = TransactionCriteria.builder()
				.billId(txn.getBillId())
				.build();

		List<Transaction> existingTxnsForBill = transactionRepository.fetchTransactions(criteria);

		for (Transaction curr : existingTxnsForBill) {
			if (curr.getTxnStatus().equals(Transaction.TxnStatusEnum.PENDING)) {
				errorMap.put("TXN_ABRUPTLY_DISCARDED",
						"A transaction for this bill has been abruptly discarded, please retry after " + (appProperties.getEarlyReconcileJobRunInterval() * 2) + " mins");
			}
			if (curr.getTxnStatus().equals(Transaction.TxnStatusEnum.SUCCESS)) {
				errorMap.put("TXN_CREATE_BILL_ALREADY_PAID", "Bill has already been paid or is in pending state");
			}
		}

	}

	private void validateTxnAmount(Transaction txn, Map<String, String> errorMap) {
		BigDecimal totalPaid = BigDecimal.ZERO;

		for (TaxAndPayment taxAndPayment : txn.getTaxAndPayments()) {
			totalPaid = totalPaid.add(taxAndPayment.getAmountPaid());
		}
		if (totalPaid.compareTo(new BigDecimal(txn.getTxnAmount())) != 0)
			errorMap.put("TXN_CREATE_INVALID_TXN_AMT", "Transaction amount should be equal to sum of all " +
					" amountPaids in taxAndPayments");
	}

	private void isGatewayActive(Transaction transaction, Map<String, String> errorMap) {
		if (!gatewayService.isGatewayActive(transaction.getGateway()))
			errorMap.put("INVALID_PAYMENT_GATEWAY", "Invalid or inactive payment gateway provided");
	}
}
