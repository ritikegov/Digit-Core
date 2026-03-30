package com.example.tradelicense.service.enrichment;

import org.digit.services.common.model.AuditDetails;
import com.example.tradelicense.client.TradeLicenseIdGenClient;
import com.example.tradelicense.web.models.TradeLicense;
import com.example.tradelicense.web.models.TradeLicenseRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class TradeLicenseEnrichmentService {

    private final TradeLicenseIdGenClient idGenClient;

    /**
     * Enrich TradeLicense before create
     */
    public void enrichCreate(TradeLicenseRequest request) {

        long now = Instant.now().toEpochMilli();

        request.getLicenses().forEach(license -> {

            // generate id if missing
            if (license.getId() == null) {
                license.setId(UUID.randomUUID().toString());
            }

            // generate application number if missing
            if (license.getApplicationNumber() == null) {
                license.setApplicationNumber(idGenClient.generateApplicationNumber(license.getTenantId()));
            }

            // default status - will be overwritten by workflow
            if (license.getStatus() == null) {
                license.setStatus("INIT");
            }

            // set application date
            if (license.getApplicationDate() == null) {
                license.setApplicationDate(now);
            }

            // set audit details
            if (license.getAuditDetails() == null) {
                AuditDetails auditDetails = new AuditDetails();
                auditDetails.setCreatedTime(now);
                auditDetails.setLastModifiedTime(now);
                license.setAuditDetails(auditDetails);
            }

            // set active trade units
            if (license.getTradeUnits() != null) {
                license.getTradeUnits().forEach(unit -> {
                    if (unit.getId() == null) {
                        unit.setId(UUID.randomUUID().toString());
                    }
                    if (unit.getActive() == null) {
                        unit.setActive(true);
                    }
                });
            }

            // set owner ids
            if (license.getOwners() != null) {
                license.getOwners().forEach(owner -> {
                    if (owner.getId() == null) {
                        owner.setId(UUID.randomUUID().toString());
                    }
                });
            }

            // set accessory ids and active status
            if (license.getAccessories() != null) {
                license.getAccessories().forEach(accessory -> {
                    if (accessory.getId() == null) {
                        accessory.setId(UUID.randomUUID().toString());
                    }
                    if (accessory.getActive() == null) {
                        accessory.setActive(true);
                    }
                });
            }

            // set document ids and active status
            if (license.getDocuments() != null) {
                license.getDocuments().forEach(doc -> {
                    if (doc.getId() == null) {
                        doc.setId(UUID.randomUUID().toString());
                    }
                    if (doc.getActive() == null) {
                        doc.setActive(true);
                    }
                });
            }

        });
    }

    /**
     * Enrich TradeLicense before update.
     * 
     * Note: This handles cases where users add NEW nested objects (trade units, owners, accessories)
     * during update. New items won't have IDs yet, so we generate them here.
     */
    public void enrichUpdate(TradeLicenseRequest request) {

        long now = Instant.now().toEpochMilli();

        request.getLicenses().forEach(license -> {

            // Update audit details - create if missing (will be merged with existing in service)
            if (license.getAuditDetails() == null) {
                AuditDetails auditDetails = new AuditDetails();
                auditDetails.setLastModifiedTime(now);
                license.setAuditDetails(auditDetails);
            } else {
                license.getAuditDetails().setLastModifiedTime(now);
            }

            // Enrich NEW trade units added during update
            if (license.getTradeUnits() != null) {
                license.getTradeUnits().forEach(unit -> {
                    if (unit.getId() == null) {
                        unit.setId(UUID.randomUUID().toString());
                        log.debug("Generated ID for new trade unit in update: {}", unit.getId());
                    }
                });
            }

            // Enrich NEW owners added during update
            if (license.getOwners() != null) {
                license.getOwners().forEach(owner -> {
                    if (owner.getId() == null) {
                        owner.setId(UUID.randomUUID().toString());
                        log.debug("Generated ID for new owner in update: {}", owner.getId());
                    }
                });
            }

            // Enrich NEW accessories added during update
            if (license.getAccessories() != null) {
                license.getAccessories().forEach(accessory -> {
                    if (accessory.getId() == null) {
                        accessory.setId(UUID.randomUUID().toString());
                        log.debug("Generated ID for new accessory in update: {}", accessory.getId());
                    }
                });
            }

            // Enrich NEW documents added during update
            if (license.getDocuments() != null) {
                license.getDocuments().forEach(document -> {
                    if (document.getId() == null) {
                        document.setId(UUID.randomUUID().toString());
                        log.debug("Generated ID for new document in update: {}", document.getId());
                    }
                });
            }

        });
    }

}
