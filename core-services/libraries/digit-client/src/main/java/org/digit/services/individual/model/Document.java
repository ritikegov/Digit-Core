package org.digit.services.individual.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    @JsonProperty("id")
    private String id;

    @JsonProperty("documentType")
    private String documentType;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("documentUid")
    private String documentUid;
}
