package com.example.tradelicense.service.impl;

import com.example.tradelicense.config.TradeLicenseConstants;
import com.example.tradelicense.service.RenewalCalculationService;
import com.example.tradelicense.web.models.Calculation;
import com.example.tradelicense.web.models.TradeLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.mdms.MdmsClient;
import org.digit.services.mdms.model.Mdms;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Set;

/**
 * Calculates renewal-specific fee adjustments.
 *
 * Renewal applications differ from new applications in that:
 *  - Early renewal (before financial year end) may attract a rebate
 *  - Late renewal (after financial year end) attracts a penalty
 *
 * Rebate/penalty percentages and date thresholds are configured in MDMS
 * under the TL.RenewalPenaltyAndRebate schema.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RenewalCalculationServiceImpl implements RenewalCalculationService {

    private final MdmsClient mdmsClient;

    @Value("${tl.mdms.rebate.schema-code:TL.Rebate}")
    private String rebateSchemaCode;

    @Value("${tl.mdms.penalty.schema-code:TL.Penalty}")
    private String penaltySchemaCode;

    @Override
    public List<Calculation.FeeDetail> calculateRenewalFees(TradeLicense license, BigDecimal baseTax) {
        List<Calculation.FeeDetail> adjustments = new ArrayList<>();

        if (baseTax == null || baseTax.compareTo(BigDecimal.ZERO) == 0) {
            return adjustments;
        }

        String financialYear = license.getFinancialYear();
        if (financialYear == null || financialYear.isBlank()) {
            log.warn("No financial year on renewal license {}, skipping rebate/penalty", license.getApplicationNumber());
            return adjustments;
        }

        long now = System.currentTimeMillis();

        // Rebate: applied when renewing before the rebate end date
        Map<String, Object> rebateMaster = fetchMaster(rebateSchemaCode, "EARLY_PAYMENT_REBATE");
        if (rebateMaster != null) {
            long rebateDeadline = getEpochFromDateString((String) rebateMaster.get("endDate"), financialYear, false);
            if (now <= rebateDeadline) {
                BigDecimal rebateAmount = calculateApplicableAmount(baseTax, rebateMaster);
                if (rebateAmount.compareTo(BigDecimal.ZERO) > 0) {
                    Calculation.FeeDetail rebate = new Calculation.FeeDetail();
                    rebate.setFeeType("TL_RENEWAL_REBATE");
                    rebate.setAmount(rebateAmount.negate()); // negative = discount
                    rebate.setDescription("Renewal rebate for early payment");
                    adjustments.add(rebate);
                    log.info("Applied renewal rebate of {} for license {}", rebateAmount, license.getApplicationNumber());
                }
            }
        }

        // Penalty: applied when renewing after the penalty start date
        Map<String, Object> penaltyMaster = fetchMaster(penaltySchemaCode, "LATE_PAYMENT_PENALTY");
        if (penaltyMaster != null) {
            long penaltyStart = getEpochFromDateString((String) penaltyMaster.get("fromDate"), financialYear, true);
            if (now >= penaltyStart) {
                BigDecimal penaltyAmount = calculateApplicableAmount(baseTax, penaltyMaster);
                if (penaltyAmount.compareTo(BigDecimal.ZERO) > 0) {
                    Calculation.FeeDetail penalty = new Calculation.FeeDetail();
                    penalty.setFeeType("TL_RENEWAL_PENALTY");
                    penalty.setAmount(penaltyAmount);
                    penalty.setDescription("Renewal penalty for late payment");
                    adjustments.add(penalty);
                    log.info("Applied renewal penalty of {} for license {}", penaltyAmount, license.getApplicationNumber());
                }
            }
        }

        return adjustments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchMaster(String schemaCode, String uniqueIdentifier) {
        try {
            List<Mdms> mdmsData = mdmsClient.searchMdmsData(schemaCode, Set.of(uniqueIdentifier));
            if (mdmsData == null || mdmsData.isEmpty()) {
                log.warn("No MDMS data found for schema: {}, identifier: {}", schemaCode, uniqueIdentifier);
                return null;
            }
            Mdms mdms = mdmsData.get(0);
            return mdms.getData() instanceof Map ? (Map<String, Object>) mdms.getData() : null;
        } catch (Exception e) {
            log.warn("Could not fetch MDMS data for schema: {}, identifier: {} - {}. Skipping.",
                    schemaCode, uniqueIdentifier, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> findApplicableMaster(String financialYear, String type,
                                                      List<Map<String, Object>> masters) {
        return masters.stream()
                .filter(m -> type.equalsIgnoreCase((String) m.get("type")))
                .filter(m -> financialYear.equals(m.get("financialYear")) || m.get("financialYear") == null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculateApplicableAmount(BigDecimal baseAmount, Map<String, Object> config) {
        Object rateObj = config.get("rate");
        if (rateObj == null) return BigDecimal.ZERO;
        BigDecimal rate = new BigDecimal(rateObj.toString());
        return baseAmount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Parses a "MM-DD" date string from MDMS config into epoch millis.
     * Uses the financial year to determine the correct calendar year.
     * e.g. endDate "06-30" in FY "2026-27" → June 30, 2026
     *      fromDate "07-01" in FY "2026-27" → July 1, 2026
     */
    private long getEpochFromDateString(String mmdd, String financialYear, boolean isPenaltyStart) {
        if (mmdd == null || financialYear == null) {
            return isPenaltyStart ? 0L : Long.MAX_VALUE;
        }
        try {
            int startYear = Integer.parseInt(financialYear.split("-")[0]);
            String[] parts = mmdd.split("-");
            int month = Integer.parseInt(parts[0]) - 1; // Calendar months are 0-based
            int day = Integer.parseInt(parts[1]);

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            // Months Apr-Mar: Apr(3)-Dec(11) belong to startYear, Jan(0)-Mar(2) to startYear+1
            int year = (month >= Calendar.APRIL) ? startYear : startYear + 1;
            cal.set(year, month, day, isPenaltyStart ? 0 : 23, isPenaltyStart ? 0 : 59, isPenaltyStart ? 0 : 59);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            log.warn("Could not parse date string {} for financial year {}", mmdd, financialYear);
            return isPenaltyStart ? 0L : Long.MAX_VALUE;
        }
    }
}
