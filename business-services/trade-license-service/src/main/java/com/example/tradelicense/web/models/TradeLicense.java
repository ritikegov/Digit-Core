package com.example.tradelicense.web.models;

import org.digit.services.common.model.AuditDetails;
import org.digit.services.workflow.model.Workflow;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeLicense {

    // Unique identifier for this trade license record (auto-generated UUID)
    @JsonProperty("id")
    private String id;

    // Geographic/administrative boundary - Keycloak realm name (e.g., "AMARAVATI", "AMARAVATI.CITY")
    // Note: In DIGIT CLI, this is called "account" but in APIs it's "tenantId"
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("applicationNumber")
    private String applicationNumber;

    @JsonProperty("licenseNumber")
    private String licenseNumber;

    // Business/shop name (e.g., "Raj Electronics", "Mumbai Sweets")
    @JsonProperty("tradeName")
    private String tradeName;

    // Business service code - always "TL" for Trade License
    // Used by Workflow, Billing, and Notification services to identify the module
    @JsonProperty("businessService")
    @Builder.Default
    private String businessService = "TL";

    @JsonProperty("licenseType")
    private String licenseType; // TEMPORARY | PERMANENT

    @JsonProperty("applicationType")
    private String applicationType; // NEW | RENEWAL

    // Current workflow state (e.g., "APPROVED", "PENDINGFORAPPROVAL") - set by Workflow Service
    @JsonProperty("status")
    private String status;

    // Workflow action to be applied (e.g., "APPLY", "APPROVE", "REJECT") - provided by user
    @JsonProperty("action")
    private String action;

    // List of Keycloak usernames or user UUIDs assigned to process this application
    @JsonProperty("assignee")
    private List<String> assignee;

    // Date when citizen submitted the application (epoch milliseconds, auto-generated)
    @JsonProperty("applicationDate")
    private Long applicationDate;

    // Date when business started/will start operations (epoch milliseconds, provided by citizen)
    @JsonProperty("commencementDate")
    private Long commencementDate;

    // License validity start date (epoch milliseconds, set when approved)
    @JsonProperty("validFrom")
    private Long validFrom;

    // License validity end date/expiry (epoch milliseconds, set when approved)
    @JsonProperty("validTo")
    private Long validTo;

    // Financial year for billing (e.g., "2024-25")
    @JsonProperty("financialYear")
    private String financialYear;

    // Reference to Property Tax system's property ID (optional)
    @JsonProperty("propertyId")
    private String propertyId;

    // Citizen's user identifier (Keycloak user UUID or username) - who owns this license
    // Note: This is NOT the same as tenantId. This identifies the individual citizen/user.
    @JsonProperty("accountId")
    private String accountId;

    // List of business owners/partners with contact details
    @JsonProperty("owners")
    private List<Owner> owners;

    // List of business activities/trade types (e.g., RETAIL, WHOLESALE) - used for fee calculation
    @JsonProperty("tradeUnits")
    private List<TradeUnit> tradeUnits;

    // Additional equipment/accessories (e.g., GENERATOR, COLD_STORAGE) - used for accessory fees
    @JsonProperty("accessories")
    private List<Accessory> accessories;

    // Type of business location: IMMOVABLE (fixed shop) or MOVABLE (mobile cart)
    @JsonProperty("structureType")
    private String structureType; // IMMOVABLE | MOVABLE

    // Additional penalty amount added manually by officer (e.g., for violations)
    @JsonProperty("adhocPenalty")
    private java.math.BigDecimal adhocPenalty;

    // Exemption/discount amount given manually by officer (e.g., for senior citizens)
    @JsonProperty("adhocExemption")
    private java.math.BigDecimal adhocExemption;

    // Business location address with geo-coordinates
    @JsonProperty("address")
    private Address address;

    // Supporting documents uploaded by citizen (stored in FileStore service)
    @JsonProperty("documents")
    private List<Document> documents;

    // Verification documents uploaded by officer during scrutiny/inspection
    @JsonProperty("verificationDocuments")
    private List<Document> verificationDocuments;

    // Workflow state and history managed by Workflow Service
    @JsonProperty("workflow")
    private Workflow workflow;

    // Audit trail: who created/modified and when
    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    // User roles from JWT token (used for workflow validation)
    @JsonProperty("roles")
    private List<String> roles;

    // Billing information for response enrichment (NOT part of domain model)
    // This field is populated dynamically from Billing Service for API responses
    @JsonProperty("billingInfo")
    private BillingInfo billingInfo;

}
