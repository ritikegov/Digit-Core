package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents a document attached to a trade license.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Document {

    // Internal identifier for this document record (auto-generated UUID)
    @JsonProperty("id")
    private String id;

    // Type of document (e.g., OWNER_PHOTO_ID, TRADE_PROOF, ADDRESS_PROOF)
    // Values come from MDMS DocumentType master
    @JsonProperty("documentType")
    private String documentType;

    // Reference to the actual file in FileStore Service (required to retrieve the file)
    @JsonProperty("fileStoreId")
    private String fileStoreId;

    // Optional: External document identifier (for integration with external document systems)
    // In most cases, this can be left null or same as 'id'
    @JsonProperty("documentUid")
    private String documentUid;

    // Whether this document is currently active (false if replaced/deleted)
    @JsonProperty("active")
    private Boolean active;
}
