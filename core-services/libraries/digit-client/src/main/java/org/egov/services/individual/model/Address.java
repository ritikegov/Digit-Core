package org.egov.services.individual.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @JsonProperty(value="addressId")
    private String addressId;
    @JsonProperty(value="streetAddress")
    private String streetAddress;
    @JsonProperty(value="area")
    private String area;
    @JsonProperty(value="city")
    private String city;
    @JsonProperty(value="state")
    private String state;
    @JsonProperty(value="countryCode")
    private String countryCode;
    @JsonProperty(value="pincode")
    private String pincode;
    @JsonProperty(value="landmark")
    private String landmark;
    @JsonProperty(value="geoLocation")
    private GeoLocation geoLocation;

    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class GeoLocation {
        @JsonProperty(value="latitude")
        private Double latitude;
        @JsonProperty(value="longitude")
        private Double longitude;
    }
}