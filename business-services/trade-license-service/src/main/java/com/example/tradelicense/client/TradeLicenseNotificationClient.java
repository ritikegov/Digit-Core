package com.example.tradelicense.client;

import org.digit.services.notification.NotificationClient;
import org.digit.services.notification.model.SendEmailRequest;
import com.example.tradelicense.web.models.TradeLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for notification-related operations.
 * Encapsulates notification client interactions for Trade License.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeLicenseNotificationClient {

    private final NotificationClient notificationClient;

    @Value("${tl.notification.template.created:tl-application-created}")
    private String templateCreated;

    @Value("${tl.notification.template.updated:tl-application-updated}")
    private String templateUpdated;

    @Value("${tl.notification.template.approved:tl-application-approved}")
    private String templateApproved;

    @Value("${tl.notification.template.payment-due:tl-payment-due}")
    private String templatePaymentDue;

    @Value("${tl.notification.template.issued:tl-license-issued}")
    private String templateIssued;

    @Value("${tl.notification.template.rejected:tl-application-rejected}")
    private String templateRejected;

    /**
     * Sends notification for trade license events.
     *
     * @param eventType the event type (TL_CREATE, TL_UPDATE, TL_APPROVE, etc.)
     * @param tenantId  the tenant ID
     * @param license   the trade license
     */
    public void sendNotification(String eventType, String tenantId, TradeLicense license) {
        log.info("Sending notification for event={}, applicationNumber={}", 
                eventType, license.getApplicationNumber());

        // Send email notification if owner has email
        if (hasOwnerEmail(license)) {
            sendEmailNotification(eventType, license);
        } else {
            log.debug("No email address found for license owners: {}", license.getApplicationNumber());
        }

        // SMS notifications could be added here if needed
        // sendSmsNotification(eventType, license);
    }

    /**
     * Sends email notification for trade license.
     *
     * @param eventType the event type
     * @param license   the trade license
     */
    private void sendEmailNotification(String eventType, TradeLicense license) {
        try {
            String ownerEmail = getOwnerEmail(license);
            if (ownerEmail == null || ownerEmail.isBlank()) {
                return;
            }

            Map<String, Object> emailPayload = createEmailPayload(eventType, license);

            SendEmailRequest request = SendEmailRequest.builder()
                    .version("v1")
                    .templateId(getTemplateId(eventType))
                    .emailIds(List.of(ownerEmail))
                    .enrich(false)
                    .payload(emailPayload)
                    .build();

            notificationClient.sendEmail(request);

            log.info("Email notification sent successfully for applicationNumber={}, event={}", 
                    license.getApplicationNumber(), eventType);

        } catch (Exception e) {
            log.error("Failed to send email notification for {}: {}", 
                    license.getApplicationNumber(), e.getMessage(), e);
            // Don't throw exception - notification failure shouldn't break the flow
        }
    }

    /**
     * Creates the email payload for notifications.
     * Maps Trade License data to template variables using Go template syntax.
     *
     * @param eventType the event type
     * @param license   the trade license
     * @return map containing email payload data
     */
    private Map<String, Object> createEmailPayload(String eventType, TradeLicense license) {
        Map<String, Object> payload = new HashMap<>();
        
        // Basic license information
        payload.put("applicationNumber", license.getApplicationNumber() != null ? license.getApplicationNumber() : "");
        payload.put("licenseNumber", license.getLicenseNumber() != null ? license.getLicenseNumber() : "");
        payload.put("tradeName", license.getTradeName() != null ? license.getTradeName() : "");
        payload.put("status", license.getStatus() != null ? license.getStatus() : "");
        payload.put("licenseType", license.getLicenseType() != null ? license.getLicenseType() : "");
        payload.put("applicationType", license.getApplicationType() != null ? license.getApplicationType() : "");
        
        // Owner information
        if (license.getOwners() != null && !license.getOwners().isEmpty()) {
            payload.put("ownerName", license.getOwners().get(0).getName() != null ? license.getOwners().get(0).getName() : "");
        } else {
            payload.put("ownerName", "");
        }
        
        // Dates (format as needed)
        payload.put("applicationDate", license.getApplicationDate() != null ? 
                formatDate(license.getApplicationDate()) : "");
        payload.put("validFrom", license.getValidFrom() != null ? 
                formatDate(license.getValidFrom()) : "");
        payload.put("validTo", license.getValidTo() != null ? 
                formatDate(license.getValidTo()) : "");
        payload.put("lastModifiedTime", license.getAuditDetails() != null && license.getAuditDetails().getLastModifiedTime() != null ? 
                formatDate(license.getAuditDetails().getLastModifiedTime()) : "");
        
        // Action information
        payload.put("action", license.getAction() != null ? license.getAction() : "");
        
        // Additional fields for specific templates
        payload.put("eventType", eventType);
        payload.put("tenantId", license.getTenantId());
        
        // Rejection reason (for rejected applications)
        payload.put("rejectionReason", "Please contact the office for details");
        
        // Payment information (placeholder - would be enriched from billing service)
        payload.put("totalAmount", "0");
        payload.put("dueDate", "");
        
        // Tracking URL
        payload.put("trackUrl", "https://tl.digit.org/track/" + license.getApplicationNumber());
        
        log.debug("Created email payload for {}: {}", license.getApplicationNumber(), payload.keySet());
        
        return payload;
    }
    
    /**
     * Formats epoch timestamp to readable date string.
     *
     * @param timestamp epoch timestamp in milliseconds
     * @return formatted date string
     */
    private String formatDate(Long timestamp) {
        if (timestamp == null) {
            return "";
        }
        
        try {
            java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            return dateTime.format(formatter);
        } catch (Exception e) {
            log.warn("Failed to format date: {}", timestamp, e);
            return timestamp.toString();
        }
    }

    /**
     * Gets the template ID based on event type.
     * Uses the configured notification template IDs from application.properties.
     *
     * @param eventType the event type
     * @return template ID
     */
    private String getTemplateId(String eventType) {
        return switch (eventType) {
            case "TL_CREATE" -> templateCreated;
            case "TL_UPDATE" -> templateUpdated;
            case "TL_APPROVE" -> templateApproved;
            case "TL_PAYMENT_DUE" -> templatePaymentDue;
            case "TL_ISSUED" -> templateIssued;
            case "TL_REJECT" -> templateRejected;
            default -> templateUpdated;
        };
    }

    /**
     * Checks if any owner has an email address.
     *
     * @param license the trade license
     * @return true if owner has email
     */
    private boolean hasOwnerEmail(TradeLicense license) {
        if (license.getOwners() == null || license.getOwners().isEmpty()) {
            return false;
        }
        
        return license.getOwners().stream()
                .anyMatch(owner -> owner.getEmailId() != null && !owner.getEmailId().isBlank());
    }

    /**
     * Gets the primary owner's email address.
     *
     * @param license the trade license
     * @return email address or null
     */
    private String getOwnerEmail(TradeLicense license) {
        if (license.getOwners() == null || license.getOwners().isEmpty()) {
            return null;
        }

        // Try to get primary owner's email first
        return license.getOwners().stream()
                .filter(owner -> Boolean.TRUE.equals(owner.getIsPrimaryOwner()))
                .map(owner -> owner.getEmailId())
                .filter(email -> email != null && !email.isBlank())
                .findFirst()
                .orElseGet(() -> 
                    // Fallback to first owner with email
                    license.getOwners().stream()
                            .map(owner -> owner.getEmailId())
                            .filter(email -> email != null && !email.isBlank())
                            .findFirst()
                            .orElse(null)
                );
    }
}
