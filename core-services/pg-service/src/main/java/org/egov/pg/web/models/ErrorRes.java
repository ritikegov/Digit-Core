package org.egov.pg.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * All APIs will return ErrorRes in case of failure which will carry Error object as actual representation of error. In case of bulk APIs, some APIs may choose to return the array of Error objects to indicate individual failure.
 */
@Validated
//@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2018-06-05T12:58:12.679+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorRes {

	@JsonProperty("Errors")
	@Valid
	private List<Error> errors = null;

}

