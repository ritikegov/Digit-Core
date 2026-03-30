package com.example.tradelicense.service;

import com.example.tradelicense.web.models.BillingInfo;
import com.example.tradelicense.web.models.TradeLicense;

/**
 * Service for enriching Trade License responses with billing data.
 * 
 * This service fetches billing information from Billing Service 3.0
 * without storing it in the Trade License domain model.
 * 
 * Following DIGIT 3.0 architectural principles:
 * - Separation of concerns: Trade License vs Billing domains
 * - Loose coupling: No billing IDs stored in license model
 * - Response enrichment: Billing data included in API responses when needed
 */
public interface BillingEnrichmentService {
    
    /**
     * Enriches trade license with billing information from Billing Service.
     * 
     * Fetches:
     * - Demand details (amount, status)
     * - Bill information (if exists)
     * - Payment history
     * - Fee breakdown
     * 
     * @param license Trade license to enrich
     * @return Billing information or null if no billing data exists
     */
    BillingInfo getBillingInfo(TradeLicense license);
    
    /**
     * Checks if trade license has any pending payments.
     * 
     * @param license Trade license to check
     * @return true if there are pending payments, false otherwise
     */
    boolean hasPendingPayments(TradeLicense license);
    
    /**
     * Gets the total amount due for a trade license.
     * 
     * @param license Trade license
     * @return Total pending amount or zero if fully paid
     */
    java.math.BigDecimal getPendingAmount(TradeLicense license);
}