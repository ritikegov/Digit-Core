package com.example.tradelicense.web.controllers;

import com.example.tradelicense.service.BillingSlabService;
import com.example.tradelicense.web.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for billing slab operations.
 * Provides API to search billing slabs from MDMS.
 * 
 * Note: Billing slabs are read-only master data configured in MDMS.
 * Create/Update operations are not supported via API.
 */
@RestController
@RequestMapping("/tl-calculator/billingslab")
@Slf4j
@RequiredArgsConstructor
public class BillingSlabController {

    private final BillingSlabService billingSlabService;

    /**
     * Searches billing slabs from Registry based on criteria.
     * 
     * @param tenantId Tenant ID (required)
     * @param licenseType License type (optional)
     * @param applicationType Application type (optional)
     * @param structureType Structure type (optional)
     * @param tradeType Trade type (optional)
     * @param accessoryCategory Accessory category (optional)
     * @param uom Unit of measurement (optional)
     * @param uomValue UOM value for range matching (optional)
     * @return BillingSlabResponse with matching slabs
     */
    @PostMapping("/_search")
    public ResponseEntity<BillingSlabResponse> searchBillingSlabs(
            @RequestParam String tenantId,
            @RequestParam(required = false) String licenseType,
            @RequestParam(required = false) String applicationType,
            @RequestParam(required = false) String structureType,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) String accessoryCategory,
            @RequestParam(required = false) String uom,
            @RequestParam(required = false) Double uomValue) {

        log.info("Received request to search billing slabs for tenantId: {}", tenantId);

        BillingSlabSearchCriteria criteria = BillingSlabSearchCriteria.builder()
                .tenantId(tenantId)
                .licenseType(licenseType)
                .applicationType(applicationType)
                .structureType(structureType)
                .tradeType(tradeType)
                .accessoryCategory(accessoryCategory)
                .uom(uom)
                .uomValue(uomValue)
                .build();

        List<BillingSlab> slabs = billingSlabService.searchBillingSlabs(criteria);

        BillingSlabResponse response = BillingSlabResponse.builder()
                .billingSlabs(slabs)
                .count(slabs.size())
                .build();

        return ResponseEntity.ok(response);
    }
}
