package com.example.tradelicense.service.impl;

import com.example.tradelicense.service.BillingSlabService;
import com.example.tradelicense.config.TradeLicenseConstants;
import com.example.tradelicense.web.models.BillingSlab;
import com.example.tradelicense.web.models.BillingSlabSearchCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.mdms.MdmsClient;
import org.digit.services.mdms.model.Mdms;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * Implementation of BillingSlabService using MDMS.
 * Billing slabs are configured in MDMS under TradeLicense module.
 * 
 * Note: This implementation fetches slabs from MDMS (read-only).
 * Slabs must be configured in MDMS JSON files and deployed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillingSlabServiceImpl implements BillingSlabService {

    private final MdmsClient mdmsClient;
    private final ObjectMapper objectMapper;
    
    @Value("${tl.billingslab.schema-code:TL.BillingSlab1}")
    private String billingSlabSchemaCode;

    /**
     * Exposes MdmsClient for other services to use.
     * Used by TradeLicenseBillingCalculatorClient for accessory rate lookup.
     */
    public MdmsClient getMdmsClient() {
        return mdmsClient;
    }

    @Override
    public List<BillingSlab> searchBillingSlabs(BillingSlabSearchCriteria criteria) {
        log.info("Searching billing slabs from MDMS with criteria: {}", criteria);

        // Try to fetch specific billing slab based on criteria
        List<BillingSlab> matchingSlabs = fetchSpecificBillingSlab(criteria);

        if (matchingSlabs.isEmpty()) {
            log.warn("No specific billing slabs found, trying to fetch all slabs and filter");
            // Fallback: fetch all slabs and filter manually
            matchingSlabs = fetchAllSlabsAndFilter(criteria);
        }

        if (matchingSlabs.isEmpty()) {
            log.error("No billing slabs found in MDMS for criteria: {}", criteria);
            return new ArrayList<>();
        }

        log.info("Found {} billing slabs matching criteria", matchingSlabs.size());
        return matchingSlabs;
    }

    /**
     * Fallback method: fetch all billing slabs and filter manually
     */
    private List<BillingSlab> fetchAllSlabsAndFilter(BillingSlabSearchCriteria criteria) {
        try {
            log.info("Fetching all billing slabs from MDMS for manual filtering");
            
            // Fetch all billing slabs without unique identifier filter
            List<Mdms> mdmsData = mdmsClient.searchMdmsData(billingSlabSchemaCode, null);

            if (mdmsData == null || mdmsData.isEmpty()) {
                log.warn("No billing slabs data found in MDMS at all");
                return new ArrayList<>();
            }

            log.info("Found {} total billing slabs in MDMS, filtering by criteria", mdmsData.size());

            // Convert MDMS data to BillingSlab objects, filter active ones, then apply criteria
            return mdmsData.stream()
                    .map(mdms -> {
                        try {
                            return objectMapper.convertValue(mdms.getData(), BillingSlab.class);
                        } catch (Exception e) {
                            log.error("Error converting MDMS data to BillingSlab: {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(slab -> slab.getActive() == null || Boolean.TRUE.equals(slab.getActive()))
                    .filter(slab -> matchesCriteria(slab, criteria))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching all billing slabs from MDMS", e);
            return new ArrayList<>();
        }
    }

    /**
     * Constructs unique identifier for billing slab based on search criteria.
     * Uses DIGIT 2.9 abbreviated format: PERM/TEMP for licenseType, IMMOV/MOV for structureType
     */
    private String constructBillingSlabIdentifier(BillingSlabSearchCriteria criteria) {
        // For accessory slabs - only need accessoryCategory and applicationType
        if (criteria.getAccessoryCategory() != null && !criteria.getAccessoryCategory().isBlank()) {
            String applicationType = criteria.getApplicationType() != null ? criteria.getApplicationType() : "NEW";
            String identifier = String.format("SLAB_%s_%s", criteria.getAccessoryCategory(), applicationType);
            log.info("Constructed accessory billing slab identifier: {} for accessoryCategory={}, applicationType={}", 
                    identifier, criteria.getAccessoryCategory(), applicationType);
            return identifier;
        }
        
        // For trade unit slabs - need tradeType, licenseType, structureType, applicationType
        if (criteria.getTradeType() == null || criteria.getLicenseType() == null || criteria.getStructureType() == null) {
            log.warn("Missing required criteria for trade unit slab: tradeType={}, licenseType={}, structureType={}", 
                    criteria.getTradeType(), criteria.getLicenseType(), criteria.getStructureType());
            return null;
        }
        
        String applicationType = criteria.getApplicationType() != null ? criteria.getApplicationType() : "NEW";
        
        String licenseTypeAbbr = TradeLicenseConstants.LICENSE_TYPE_PERMANENT.equalsIgnoreCase(criteria.getLicenseType())
                ? TradeLicenseConstants.LICENSE_TYPE_ABBR_PERMANENT : TradeLicenseConstants.LICENSE_TYPE_ABBR_TEMPORARY;
        String structureTypeAbbr = TradeLicenseConstants.STRUCTURE_TYPE_IMMOVABLE.equalsIgnoreCase(criteria.getStructureType())
                ? TradeLicenseConstants.STRUCTURE_TYPE_ABBR_IMMOVABLE : TradeLicenseConstants.STRUCTURE_TYPE_ABBR_MOVABLE;
        
        String identifier = String.format("SLAB_%s_%s_%s_%s", 
                criteria.getTradeType(), licenseTypeAbbr, structureTypeAbbr, applicationType);
        
        log.info("Constructed trade unit billing slab identifier: {} for tradeType={}, licenseType={}, structureType={}, applicationType={}", 
                identifier, criteria.getTradeType(), criteria.getLicenseType(), criteria.getStructureType(), applicationType);
        
        return identifier;
    }

    /**
     * Fetches specific billing slab from MDMS based on constructed identifier
     */
    private List<BillingSlab> fetchSpecificBillingSlab(BillingSlabSearchCriteria criteria) {
        try {
            String uniqueIdentifier = constructBillingSlabIdentifier(criteria);
            
            if (uniqueIdentifier == null) {
                log.warn("Cannot construct billing slab identifier - missing required criteria");
                return new ArrayList<>();
            }
            
            log.info("Fetching billing slab with identifier: {}", uniqueIdentifier);
            
            List<Mdms> mdmsData = mdmsClient.searchMdmsData(billingSlabSchemaCode, Set.of(uniqueIdentifier));

            if (mdmsData == null || mdmsData.isEmpty()) {
                    log.warn("No billing slab found even with fallback approach");
                    return new ArrayList<>();
            }

            // Convert MDMS data to BillingSlab objects and filter active ones
            return mdmsData.stream()
                    .map(mdms -> objectMapper.convertValue(mdms.getData(), BillingSlab.class))
                    .filter(slab -> slab.getActive() == null || Boolean.TRUE.equals(slab.getActive()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching specific billing slab from MDMS", e);
            throw new RuntimeException("Failed to fetch billing slab from MDMS: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a slab matches the search criteria (DIGIT 2.9 style - simple matching)
     */
    private boolean matchesCriteria(BillingSlab slab, BillingSlabSearchCriteria criteria) {
        // License type match
        if (criteria.getLicenseType() != null && 
            !criteria.getLicenseType().equalsIgnoreCase(slab.getLicenseType())) {
            return false;
        }

        // Application type match (if slab has applicationType specified, it must match)
        if (criteria.getApplicationType() != null && slab.getApplicationType() != null &&
            !criteria.getApplicationType().equalsIgnoreCase(slab.getApplicationType())) {
            return false;
        }

        // Structure type match
        if (criteria.getStructureType() != null && 
            !criteria.getStructureType().equalsIgnoreCase(slab.getStructureType())) {
            return false;
        }

        // Trade type match (for trade units)
        if (criteria.getTradeType() != null && 
            !criteria.getTradeType().equalsIgnoreCase(slab.getTradeType())) {
            return false;
        }

        // Accessory category match (for accessories)
        if (criteria.getAccessoryCategory() != null && 
            !criteria.getAccessoryCategory().equalsIgnoreCase(slab.getAccessoryCategory())) {
            return false;
        }

        // UOM match (if slab has UOM specified, it must match)
        if (criteria.getUom() != null && slab.getUom() != null &&
            !criteria.getUom().equalsIgnoreCase(slab.getUom())) {
            return false;
        }

        // UOM value range match (inclusive)
        if (criteria.getUomValue() != null) {
            if (slab.getFromUom() != null && criteria.getUomValue() < slab.getFromUom()) {
                return false;
            }
            if (slab.getToUom() != null && criteria.getUomValue() > slab.getToUom()) {
                return false;
            }
        }

        return true;
    }
}
