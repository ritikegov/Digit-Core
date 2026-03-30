package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.digit.services.workflow.model.Workflow;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLicenseWrapper {

    @JsonProperty("tradeLicense")
    @JsonAlias({"TradeLicense"})
    @NotNull(message = "TradeLicense cannot be null")
    @Valid
    private TradeLicense tradeLicense;

    @JsonProperty("workflow")
    @JsonAlias({"Workflow"})
    @Valid
    private Workflow workflow;
}