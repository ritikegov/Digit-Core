package org.digit.services.billing;

import org.digit.services.billing.model.*;
import org.digit.services.config.ApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

public class BillingClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    public BillingClient(RestTemplate restTemplate, ApiProperties apiProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }

    // ── Demands ───────────────────────────────────────────────────────────────

    public Demand createDemand(DemandCreate demandCreate) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/demands";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, demandCreate, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Demand.class);
    }

    public Demand updateDemand(DemandUpdate demandUpdate) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/demands";
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(demandUpdate), JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Demand.class);
    }

    public Demand patchDemand(String demandId, DemandPatch demandPatch) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/demands/" + demandId;
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(demandPatch), JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Demand.class);
    }

    public Demand getDemandById(String demandId) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/demands/" + demandId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Demand.class);
    }

    public List<Demand> searchDemands(String businessServiceCode, String consumerCode) {
        String url = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/demands")
                .queryParam("businessServiceCode", businessServiceCode)
                .queryParam("consumerCode", consumerCode)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Demand.class));
    }

    public List<Demand> searchDemandsWithFilters(String businessServiceCode, String consumerCode,
                                                  String status, Long periodFrom, Long periodTo,
                                                  int limit, int offset) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/demands")
                .queryParam("businessServiceCode", businessServiceCode)
                .queryParam("consumerCode", consumerCode)
                .queryParam("limit", limit)
                .queryParam("offset", offset);
        if (status != null) builder.queryParam("status", status);
        if (periodFrom != null) builder.queryParam("periodFrom", periodFrom);
        if (periodTo != null) builder.queryParam("periodTo", periodTo);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Demand.class));
    }

    public Demand freezeDemand(String demandId) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/demands/" + demandId + "/freeze";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Demand.class);
    }

    public Demand cancelDemand(String demandId, String reasonCode, String note) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/demands/" + demandId + "/cancel";
        CancelDemandRequest request = CancelDemandRequest.builder().reasonCode(reasonCode).note(note).build();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Demand.class);
    }

    public Demand cancelDemand(String demandId) {
        return cancelDemand(demandId, null, null);
    }

    // ── Bills ─────────────────────────────────────────────────────────────────

    public Bill generateBill(GenerateBillCriteria criteria) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/bills/generate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, criteria, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Bill.class);
    }

    public List<Bill> searchBills(String businessServiceCode, String consumerCode) {
        String url = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/bills")
                .queryParam("businessServiceCode", businessServiceCode)
                .queryParam("consumerCode", consumerCode)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Bill.class));
    }

    public Bill getBillById(String billId) {
        String url = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/bills")
                .queryParam("billIds", billId)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        List<Bill> bills = objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Bill.class));
        return bills != null && !bills.isEmpty() ? bills.get(0) : null;
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    public Payment createPayment(PaymentCreate paymentCreate) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/payments";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, paymentCreate, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Payment.class);
    }

    public List<Payment> searchPayments(String businessServiceCode, String consumerCode) {
        String url = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/payments")
                .queryParam("businessServiceCode", businessServiceCode)
                .queryParam("consumerCode", consumerCode)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Payment.class));
    }

    public Payment getPaymentById(String paymentId) {
        String url = apiProperties.getBillingServiceUrl() + "/billing/v3/payments/" + paymentId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return objectMapper.convertValue(response.getBody(), Payment.class);
    }

    // ── Business Services ─────────────────────────────────────────────────────

    public List<BusinessService> searchBusinessServices(String code) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/business-services");
        if (code != null) builder.queryParam("code", code);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, BusinessService.class));
    }

    // ── Tax Heads ─────────────────────────────────────────────────────────────

    public List<TaxHead> searchTaxHeads(String businessServiceCode, String code, String category) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(apiProperties.getBillingServiceUrl() + "/billing/v3/tax-heads");
        if (businessServiceCode != null) builder.queryParam("businessServiceCode", businessServiceCode);
        if (code != null) builder.queryParam("code", code);
        if (category != null) builder.queryParam("category", category);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return objectMapper.convertValue(response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TaxHead.class));
    }
}
