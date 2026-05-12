package org.digit.services.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @JsonProperty("documentType")
    private String documentType;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("documentUid")
    private String documentUid;

    @JsonProperty("additionalDetails")
    private Map<String, Object> additionalDetails;
}
