package com.example.tradelicense.validation;

import com.example.tradelicense.web.models.Accessory;
import com.example.tradelicense.web.models.Document;
import com.example.tradelicense.web.models.TradeLicense;
import com.example.tradelicense.web.models.TradeLicenseRequest;
import com.example.tradelicense.web.models.TradeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.mdms.MdmsClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for MDMS master data validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdmsValidator {

    private final MdmsClient mdmsClient;

    /**
     * Validates MDMS data for all licenses in the request.
     *
     * @param request the trade license request
     */
    public void validate(TradeLicenseRequest request) {
        request.getLicenses().forEach(this::validateMdmsData);
    }

    /**
     * Validates MDMS data for a single trade license.
     *
     * @param license the trade license to validate
     * @throws RuntimeException if validation fails
     */
    public void validateMdmsData(TradeLicense license) {
        Map<String, String> errorMap = new HashMap<>();

        // Validate license type
        if (license.getLicenseType() != null && !license.getLicenseType().isBlank()) {
            boolean isValid = mdmsClient.isMdmsDataValid(
                    "TL.LicenseType",
                    Set.of(license.getLicenseType())
            );
            if (!isValid) {
                errorMap.put("INVALID_LICENSETYPE", 
                        "The license type '" + license.getLicenseType() + "' does not exist");
            }
        }

        // Validate structure type
        if (license.getStructureType() != null && !license.getStructureType().isBlank()) {
            boolean isValid = mdmsClient.isMdmsDataValid(
                    "TL.StructureType",
                    Set.of(license.getStructureType())
            );
            if (!isValid) {
                errorMap.put("INVALID_STRUCTURETYPE", 
                        "The structure type '" + license.getStructureType() + "' does not exist");
            }
        }

        // Validate trade units
        if (license.getTradeUnits() != null && !license.getTradeUnits().isEmpty()) {
            validateTradeUnits(license, errorMap);
        }

        // Validate accessories
        if (license.getAccessories() != null && !license.getAccessories().isEmpty()) {
            validateAccessories(license, errorMap);
        }

        // Validate document types
        if (license.getDocuments() != null && !license.getDocuments().isEmpty()) {
            validateDocuments(license, errorMap);
        }

        if (!errorMap.isEmpty()) {
            log.error("MDMS validation failed for license: {}, errors: {}", 
                    license.getApplicationNumber(), errorMap);
            throw new RuntimeException("MDMS validation failed: " + errorMap);
        }
    }

    /**
     * Validates trade units against MDMS.
     */
    private void validateTradeUnits(TradeLicense license, Map<String, String> errorMap) {
        // Collect all trade types
        Set<String> tradeTypes = license.getTradeUnits().stream()
                .map(TradeUnit::getTradeType)
                .filter(type -> type != null && !type.isBlank())
                .collect(Collectors.toSet());

        // Validate trade types exist in MDMS
        if (!tradeTypes.isEmpty()) {
            boolean isValid = mdmsClient.isMdmsDataValid("TL.TradeType", tradeTypes);
            if (!isValid) {
                errorMap.put("INVALID_TRADETYPE", 
                        "One or more trade types are invalid: " + tradeTypes);
            }
        }

        // Validate each trade unit
        license.getTradeUnits().forEach(unit -> {
            if (unit.getTradeType() == null || unit.getTradeType().isBlank()) {
                errorMap.put("NULL_TRADETYPE", "Trade type cannot be null or empty");
            }

            // Validate UOM and UOM value
            if (unit.getUom() != null && unit.getUomValue() == null) {
                errorMap.put("INVALID_UOMVALUE", 
                        "UOM value cannot be null when UOM is specified for trade type: " + unit.getTradeType());
            }
        });
    }

    /**
     * Validates accessories against MDMS.
     */
    private void validateAccessories(TradeLicense license, Map<String, String> errorMap) {
        // Collect all accessory categories
        Set<String> accessoryCategories = license.getAccessories().stream()
                .map(Accessory::getAccessoryCategory)
                .filter(category -> category != null && !category.isBlank())
                .collect(Collectors.toSet());

        // Validate accessory categories exist in MDMS
        if (!accessoryCategories.isEmpty()) {
            boolean isValid = mdmsClient.isMdmsDataValid("TL.AccessoryCategory", accessoryCategories);
            if (!isValid) {
                errorMap.put("INVALID_ACCESSORYCATEGORY", 
                        "One or more accessory categories are invalid: " + accessoryCategories);
            }
        }

        // Validate each accessory
        license.getAccessories().forEach(accessory -> {
            if (accessory.getAccessoryCategory() == null || accessory.getAccessoryCategory().isBlank()) {
                errorMap.put("NULL_ACCESSORYCATEGORY", "Accessory category cannot be null or empty");
            }

            // Validate UOM and UOM value
            if (accessory.getUom() != null && accessory.getUomValue() == null) {
                errorMap.put("INVALID_ACCESSORY_UOMVALUE", 
                        "UOM value cannot be null when UOM is specified for accessory: " + accessory.getAccessoryCategory());
            }
        });
    }

    /**
     * Validates document types against MDMS.
     */
    private void validateDocuments(TradeLicense license, Map<String, String> errorMap) {
        // Collect all document types
        Set<String> documentTypes = license.getDocuments().stream()
                .map(Document::getDocumentType)
                .filter(type -> type != null && !type.isBlank())
                .collect(Collectors.toSet());

        // Validate document types exist in MDMS
        if (!documentTypes.isEmpty()) {
            boolean isValid = mdmsClient.isMdmsDataValid("TL.DocumentType", documentTypes);
            if (!isValid) {
                errorMap.put("INVALID_DOCUMENTTYPE", 
                        "One or more document types are invalid: " + documentTypes);
            }
        }

        // Validate each document
        license.getDocuments().forEach(document -> {
            if (document.getDocumentType() == null || document.getDocumentType().isBlank()) {
                errorMap.put("NULL_DOCUMENTTYPE", "Document type cannot be null or empty");
            }
        });
    }
}
