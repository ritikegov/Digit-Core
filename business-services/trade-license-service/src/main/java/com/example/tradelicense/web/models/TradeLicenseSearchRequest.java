package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLicenseSearchRequest {

    @JsonProperty("searchCriteria")
    @Valid
    @NotNull(message = "Search criteria is required")
    private TradeLicenseSearchCriteria searchCriteria;

}