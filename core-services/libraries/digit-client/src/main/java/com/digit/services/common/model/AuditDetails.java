package com.digit.services.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditDetails {

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("createdTime")
    private Long createdTime;

    @JsonProperty("modifiedBy")
    private String modifiedBy;

    @JsonProperty("modifiedTime")
    private Long modifiedTime;

    // Alias getters for backward compatibility with lastModifiedBy/lastModifiedTime field names
    public String getLastModifiedBy() { return modifiedBy; }
    public Long getLastModifiedTime() { return modifiedTime; }
    public void setLastModifiedBy(String v) { this.modifiedBy = v; }
    public void setLastModifiedTime(Long v) { this.modifiedTime = v; }
}
