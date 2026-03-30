package com.example.tradelicense.service;

import com.example.tradelicense.web.models.BillingSlab;
import com.example.tradelicense.web.models.BillingSlabSearchCriteria;

import java.util.List;

/**
 * Service interface for billing slab operations.
 * 
 * BillingSlabs are master/configuration data stored in MDMS (Master Data Management Service).
 * MDMS is appropriate for BillingSlabs because:
 * - They are configuration data (fee rates/rules), not transactional data
 * - They change infrequently (typically annually when government updates rates)
 * - Changes need approval and audit trail (Git version control)
 * - They are tenant-specific (different cities have different rates)
 * 
 * This follows DIGIT 2.9 patterns where BillingSlabs were stored in MDMS.
 * 
 * Note: BillingSlabs are read-only via API. They must be configured in MDMS JSON files
 * and deployed. This ensures controlled changes to financial configuration with proper
 * approval and audit trail.
 */
public interface BillingSlabService {
    
    /**
     * Searches billing slabs from MDMS based on criteria.
     * 
     * Fetches slabs from MDMS and filters based on:
     * - licenseType (PERMANENT, TEMPORARY)
     * - applicationType (NEW, RENEWAL)
     * - structureType (IMMOVABLE, MOVABLE)
     * - tradeType (RETAIL, WHOLESALE, etc.)
     * - accessoryCategory (SIGNBOARD, HOARDING, etc.)
     * - uom and uomValue (for range matching)
     * 
     * @param criteria Search criteria for filtering slabs
     * @return List of matching billing slabs from MDMS
     */
    List<BillingSlab> searchBillingSlabs(BillingSlabSearchCriteria criteria);
}
