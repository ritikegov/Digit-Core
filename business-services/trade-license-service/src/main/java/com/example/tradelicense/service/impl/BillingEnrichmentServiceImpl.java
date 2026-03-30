package com.example.tradelicense.service.impl;

import com.example.tradelicense.client.TradeLicenseBillingCalculatorClient;
import com.example.tradelicense.service.BillingEnrichmentService;
import com.example.tradelicense.web.models.BillingInfo;
import com.example.tradelicense.web.models.TradeLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingEnrichmentServiceImpl implements BillingEnrichmentService {

    private final TradeLicenseBillingCalculatorClient billingClient;

    @Override
    public BillingInfo getBillingInfo(TradeLicense license) {
        List<Map<String, Object>> demands = billingClient.searchDemands(
                license.getApplicationNumber(), license.getTenantId());

        if (demands.isEmpty()) {
            log.debug("No demands found for license: {}", license.getApplicationNumber());
            return null;
        }

        Map<String, Object> demand = demands.get(0);

        BillingInfo.BillingInfoBuilder builder = BillingInfo.builder()
                .demandId((String) demand.get("id"))
                .demandStatus((String) demand.get("status"))
                .totalAmount(new BigDecimal(demand.get("totalAmount").toString()))
                .paidAmount(new BigDecimal(demand.getOrDefault("paidAmount", "0").toString()))
                .pendingAmount(new BigDecimal(demand.getOrDefault("pendingAmount", demand.get("totalAmount")).toString()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) demand.get("lineItems");
        if (lineItems != null && !lineItems.isEmpty()) {
            builder.feeDetails(lineItems);
        }

        List<Map<String, Object>> bills = billingClient.searchBills(license);
        if (!bills.isEmpty()) {
            Map<String, Object> bill = bills.get(0);
            builder.billId((String) bill.get("id"))
                   .billNumber((String) bill.get("billNumber"))
                   .billDate(bill.get("billDate") != null ? Long.valueOf(bill.get("billDate").toString()) : null)
                   .billStatus((String) bill.get("status"));
        }

        List<Map<String, Object>> payments = billingClient.searchPayments(license);
        if (!payments.isEmpty()) {
            builder.payments(payments);
        }

        BillingInfo billingInfo = builder.build();
        log.info("Enriched billing info for license: {} demand: {} status: {} pending: {}",
                license.getApplicationNumber(), billingInfo.getDemandId(),
                billingInfo.getDemandStatus(), billingInfo.getPendingAmount());
        return billingInfo;
    }

    @Override
    public boolean hasPendingPayments(TradeLicense license) {
        BillingInfo billingInfo = getBillingInfo(license);
        return billingInfo != null &&
               billingInfo.getPendingAmount() != null &&
               billingInfo.getPendingAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public BigDecimal getPendingAmount(TradeLicense license) {
        BillingInfo billingInfo = getBillingInfo(license);
        return billingInfo != null ? billingInfo.getPendingAmount() : BigDecimal.ZERO;
    }
}
