package com.example.tradelicense.web.models;

import org.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Owner {

@JsonProperty("id")
private String id;

@JsonProperty("name")
private String name;

@JsonProperty("mobileNumber")
private String mobileNumber;

@JsonProperty("emailId")
private String emailId;

@JsonProperty("gender")
private String gender;

@JsonProperty("ownerType")
private String ownerType;

@JsonProperty("isPrimaryOwner")
private Boolean isPrimaryOwner;

@JsonProperty("ownershipPercentage")
private Double ownershipPercentage;

// Institution-specific fields — populated only when ownerType is INSTITUTIONAL
@JsonProperty("institutionName")
private String institutionName;

@JsonProperty("institutionType")
private String institutionType; // e.g. COMPANY, TRUST, SOCIETY, GOVERNMENT

@JsonProperty("designationOfAuthorizedPerson")
private String designationOfAuthorizedPerson;

@JsonProperty("nameOfAuthorizedPerson")
private String nameOfAuthorizedPerson;

@JsonProperty("contactNo")
private String contactNo;

@JsonProperty("auditDetails")
private AuditDetails auditDetails;

}
