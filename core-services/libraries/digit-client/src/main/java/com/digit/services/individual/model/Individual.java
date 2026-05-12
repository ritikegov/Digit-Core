package com.digit.services.individual.model;

import com.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Individual {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("dateOfBirth")
    private String dateOfBirth;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @JsonProperty("email")
    private String email;

    @JsonProperty("address")
    private Address address;

    @JsonProperty("documents")
    private List<Document> documents;

    @JsonProperty("auditDetail")
    private AuditDetails auditDetail;

    @JsonProperty("additionalFields")
    private Map<String, Object> additionalFields;
}
