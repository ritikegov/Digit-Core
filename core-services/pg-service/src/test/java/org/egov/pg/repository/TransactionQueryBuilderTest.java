package org.egov.pg.repository;

import org.egov.pg.models.Transaction;
import org.egov.pg.web.models.TransactionCriteria;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionQueryBuilderTest {

	@Test
	void getPaymentSearchQueryByCreatedTimeRange_withFiltersPaginationAndOrder_buildsExpectedQueryAndParams() {
		TransactionCriteria criteria = TransactionCriteria.builder()
				.tenantId("pb")
				.txnId("TXN-1")
				.userUuid("u-1")
				.billId("B-1")
				.txnStatus(Transaction.TxnStatusEnum.SUCCESS)
				.consumerCode("PT-1")
				.receipt("REC-1")
				.limit(10)
				.offset(5)
				.build();
		List<Object> params = new ArrayList<>();

		String query = TransactionQueryBuilder.getPaymentSearchQueryByCreatedTimeRange(criteria, params);

		assertThat(query).contains("WHERE")
				.contains("order by pg.created_time desc")
				.contains("limit ?")
				.contains("offset ?");
		assertThat(params).contains("pb", "TXN-1", "u-1", "B-1", "SUCCESS", "PT-1", "REC-1", 10, 5);
	}

	@Test
	void getPaymentSearchQueryByCreatedTimeRange_withoutFilters_onlyOrdersAndNoParams() {
		TransactionCriteria criteria = TransactionCriteria.builder().build();
		List<Object> params = new ArrayList<>();

		String query = TransactionQueryBuilder.getPaymentSearchQueryByCreatedTimeRange(criteria, params);

		assertThat(query).doesNotContain(" WHERE ").contains("order by pg.created_time desc");
		assertThat(params).isEmpty();
	}

	@Test
	void getPaymentSearchQueryByCreatedTimeRange_forTimeRange_addsRangeConditions() {
		TransactionCriteria criteria = TransactionCriteria.builder().tenantId("pb").build();
		List<Object> params = new ArrayList<>();

		String query = TransactionQueryBuilder.getPaymentSearchQueryByCreatedTimeRange(criteria, 100L, 200L, params);

		assertThat(query).contains("pg.created_time >= ?").contains("pg.created_time <= ?");
		assertThat(params).containsExactly("pb", 100L, 200L);
	}

	@Test
	void getPaymentSearchQueryByCreatedTimeRange_forTimeRangeWithoutExistingWhere_createsWhereClause() {
		TransactionCriteria criteria = TransactionCriteria.builder().build();
		List<Object> params = new ArrayList<>();

		String query = TransactionQueryBuilder.getPaymentSearchQueryByCreatedTimeRange(criteria, 10L, 20L, params);

		assertThat(query).contains(" WHERE ").contains("pg.created_time >= ?");
		assertThat(params).containsExactly(10L, 20L);
	}
}