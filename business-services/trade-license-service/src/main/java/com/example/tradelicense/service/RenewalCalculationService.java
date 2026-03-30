package com.example.tradelicense.service;

import com.example.tradelicense.web.models.Calculation;
import com.example.tradelicense.web.models.TradeLicense;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for calculating renewal-specific fees (rebates and penalties).
 * Renewal applications may attract rebates for early payment or
 * penalties for late renewal, distinct from new application fee logic.
 */
public interface RenewalCalculationService {

    /**
     * Calculates renewal-specific fee adjustments (rebates and penalties)
     * based on payment timing relative to the financial year.
     *
     * @param license  the trade license being renewed
     * @param baseTax  base tax amount before adjustments
     * @return list of fee details representing rebates (negative) and penalties (positive)
     */
    List<Calculation.FeeDetail> calculateRenewalFees(TradeLicense license, BigDecimal baseTax);
}
