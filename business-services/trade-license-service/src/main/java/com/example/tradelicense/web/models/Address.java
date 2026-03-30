package com.example.tradelicense.web.models;

import org.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    // Unique identifier for this address (optional, for tracking purposes)
    @JsonProperty("id")
    private String id;

    // Building/Door number
    @JsonProperty("doorNo")
    private String doorNo;

    // Building name
    @JsonProperty("buildingName")
    private String buildingName;

    // Street name
    @JsonProperty("street")
    private String street;

    // Full address string (concatenated from above fields)
    @JsonProperty("address")
    private String address;

    // Administrative boundary fields (DIGIT 2.9 compatibility)
    @JsonProperty("locality")
    private String locality;

    @JsonProperty("localityCode")
    private String localityCode;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("wardCode")
    private String wardCode;

    @JsonProperty("zone")
    private String zone;

    @JsonProperty("zoneCode")
    private String zoneCode;

    @JsonProperty("city")
    private String city;

    @JsonProperty("cityCode")
    private String cityCode;

    @JsonProperty("district")
    private String district;

    @JsonProperty("districtCode")
    private String districtCode;

    @JsonProperty("state")
    private String state;

    @JsonProperty("stateCode")
    private String stateCode;

    @JsonProperty("pincode")
    private String pincode;

    // Geo-coordinates for mapping and location services
    @JsonProperty("latitude")
    private BigDecimal latitude;

    @JsonProperty("longitude")
    private BigDecimal longitude;

    // Boundary type for validation (WARD, ZONE, LOCALITY, etc.)
    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}