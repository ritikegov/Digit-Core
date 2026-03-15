package org.egov.pg.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Error object will be returned as a part of response body ErrorResponse whenever the request processing status is FAILED. HTTP return in this scenario will usually be HTTP 400.
 */
@Validated
//@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2018-06-05T12:58:12.679+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Error {
	@JsonProperty("code")
	@NotNull
	private String code = null;

	@JsonProperty("message")
	@NotNull
	private String message = null;

	@JsonProperty("description")

	private String description = null;

	@JsonProperty("params")
	@Valid
	private List<String> params = null;


}

