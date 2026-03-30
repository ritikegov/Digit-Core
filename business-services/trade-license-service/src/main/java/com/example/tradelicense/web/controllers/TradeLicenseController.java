package com.example.tradelicense.web.controllers;

import com.example.tradelicense.service.TradeLicenseService;
import com.example.tradelicense.service.BillingEnrichmentService;
import com.example.tradelicense.client.TradeLicenseBillingCalculatorClient;
import com.example.tradelicense.web.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;

@RestController
@RequestMapping("/trade-license")
@RequiredArgsConstructor
@Slf4j
@RestControllerAdvice
public class TradeLicenseController {

    private final TradeLicenseService tradeLicenseService;
    private final TradeLicenseBillingCalculatorClient billingCalculatorClient;
    private final BillingEnrichmentService billingEnrichmentService;

    private static final Pattern REALM_PATTERN = Pattern.compile(".*/realms/([^/]+)/*$");

    // ----------------------- ROLE EXTRACTION -----------------------
    private List<String> getRealmRoles(Jwt jwt) {

        if (jwt == null) return List.of();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

        if (realmAccess == null) return List.of();

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());

        return roles;
    }

    // ----------------------- TENANT EXTRACTION -----------------------
    private static String getTenantIdFromIssuer(Jwt jwt) {

        if (jwt == null || jwt.getIssuer() == null) return null;

        String iss = jwt.getIssuer().toString();

        Matcher m = REALM_PATTERN.matcher(iss);

        if (m.matches()) return m.group(1);

        try {
            String path = URI.create(iss).getPath();
            Matcher m2 = REALM_PATTERN.matcher(path);

            if (m2.matches()) return m2.group(1);

        } catch (Exception ignored) {}

        return null;
    }

    // ----------------------- CREATE -----------------------
    @PostMapping("/create")
    public ResponseEntity<TradeLicenseResponse> create(
            @Valid @RequestBody TradeLicenseWrapper wrapper,
            @AuthenticationPrincipal Jwt jwt
    ) {

        List<String> roles = getRealmRoles(jwt);
        String tenantId = getTenantIdFromIssuer(jwt);

        if (tenantId != null)
            wrapper.getTradeLicense().setTenantId(tenantId);
        else
            log.warn("create: tenantId could not be resolved from JWT issuer");

        // Set user roles from JWT token for workflow validation
        wrapper.getTradeLicense().setRoles(roles);

        log.debug("create: roles={}, tenant={}", roles, tenantId);

        // Extract workflow action from wrapper and set on license
        if (wrapper.getWorkflow() != null && wrapper.getWorkflow().getAction() != null) {
            wrapper.getTradeLicense().setAction(wrapper.getWorkflow().getAction());
        }

        TradeLicenseRequest request =
                TradeLicenseRequest.builder()
                        .licenses(List.of(wrapper.getTradeLicense()))
                        .build();

        TradeLicenseResponse response = tradeLicenseService.create(request);

        // Enrich response with billing information if enabled
        enrichResponseWithBillingInfo(response);

        return ResponseEntity.ok(response);
    }

    // ----------------------- UPDATE -----------------------
    @PostMapping("/update")
    public ResponseEntity<TradeLicenseResponse> update(
            @Valid @RequestBody TradeLicenseWrapper wrapper,
            @AuthenticationPrincipal Jwt jwt
    ) {

        List<String> roles = getRealmRoles(jwt);
        String tenantId = getTenantIdFromIssuer(jwt);

        if (tenantId != null)
            wrapper.getTradeLicense().setTenantId(tenantId);
        else
            log.warn("update: tenantId could not be resolved from JWT issuer");

        // Set user roles from JWT token for workflow validation
        wrapper.getTradeLicense().setRoles(roles);

        // Extract workflow action from wrapper and set on license
        if (wrapper.getWorkflow() != null && wrapper.getWorkflow().getAction() != null) {
            wrapper.getTradeLicense().setAction(wrapper.getWorkflow().getAction());
        }

        log.debug("update: applicationNumber={}, roles={}, tenant={}",
                wrapper.getTradeLicense().getApplicationNumber(), roles, tenantId);

        TradeLicenseRequest request =
                TradeLicenseRequest.builder()
                        .licenses(List.of(wrapper.getTradeLicense()))
                        .build();

        TradeLicenseResponse response = tradeLicenseService.update(request);

        // Enrich response with billing information if enabled
        enrichResponseWithBillingInfo(response);

        return ResponseEntity.ok(response);
    }

    // ----------------------- SEARCH -----------------------
    @GetMapping("/search")
    public ResponseEntity<TradeLicenseResponse> search(
            @RequestParam(required = false) String applicationNumber,
            @RequestParam(required = false) String mobileNumber,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "ASC") String sortOrder,
            @AuthenticationPrincipal Jwt jwt
    ) {

        List<String> roles = getRealmRoles(jwt);
        String tenantId = getTenantIdFromIssuer(jwt);

        if (tenantId == null)
            log.warn("search: tenantId could not be resolved from JWT issuer");

        log.debug("search: applicationNumber={}, mobileNumber={}, tenant={}, roles={}, offset={}, limit={}",
                applicationNumber, mobileNumber, tenantId, roles, offset, limit);

        TradeLicenseSearchCriteria criteria =
                TradeLicenseSearchCriteria.builder()
                        .tenantId(tenantId)
                        .applicationNumber(applicationNumber)
                        .mobileNumber(mobileNumber)
                        .status(status)
                        .offset(offset)
                        .limit(limit)
                        .sortBy(sortBy)
                        .sortOrder(sortOrder)
                        .build();

        TradeLicenseResponse response = tradeLicenseService.search(criteria);

        // Enrich response with billing information if enabled
        enrichResponseWithBillingInfo(response);

        return ResponseEntity.ok(response);
    }

    // ----------------------- CALCULATE FEES (PREVIEW) -----------------------
    @PostMapping("/_calculate")
    public ResponseEntity<Calculation> calculateFees(
            @Valid @RequestBody TradeLicenseWrapper wrapper,
            @AuthenticationPrincipal Jwt jwt
    ) {

        List<String> roles = getRealmRoles(jwt);
        String tenantId = getTenantIdFromIssuer(jwt);

        if (tenantId != null)
            wrapper.getTradeLicense().setTenantId(tenantId);
        else
            log.warn("calculateFees: tenantId could not be resolved from JWT issuer");

        log.debug("calculateFees: tradeName={}, tenant={}, roles={}",
                wrapper.getTradeLicense().getTradeName(), tenantId, roles);

        // Calculate fees without creating application or demand - use billing calculator directly
        Calculation calculation = billingCalculatorClient.calculateTradeLicenseFee(wrapper.getTradeLicense());

        return ResponseEntity.ok(calculation);
    }

    // ----------------------- VALIDATION HANDLERS -----------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {

        log.warn("Validation error: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> ErrorResponse.FieldError.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .code("VALIDATION_ERROR")
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .description("One or more fields have validation errors")
                .errors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {

        log.warn("Constraint violation: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(v -> ErrorResponse.FieldError.builder()
                        .field(v.getPropertyPath().toString())
                        .message(v.getMessage())
                        .code("CONSTRAINT_VIOLATION")
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("CONSTRAINT_VIOLATION")
                .message("Constraint violation")
                .description("One or more constraints were violated")
                .errors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {

        log.error("Runtime exception: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message(ex.getMessage())
                .description("An error occurred while processing the request")
                .errors(Collections.emptyList())
                .build();

        return ResponseEntity.status(500).body(errorResponse);
    }

    // ----------------------- BILLING ENRICHMENT -----------------------
    
    /**
     * Enriches Trade License response with billing information.
     * This follows DIGIT 3.0 pattern of response enrichment without tight coupling.
     * Gracefully handles cases where billing service is unavailable.
     */
    private void enrichResponseWithBillingInfo(TradeLicenseResponse response) {
        if (response == null || response.getLicenses() == null) {
            return;
        }

        for (TradeLicense license : response.getLicenses()) {
            try {
                BillingInfo billingInfo = billingEnrichmentService.getBillingInfo(license);
                if (billingInfo != null) {
                    license.setBillingInfo(billingInfo);
                    log.debug("✅ Enriched billing info for license: {} with demand: {}", 
                            license.getApplicationNumber(), billingInfo.getDemandId());
                } else {
                    log.debug("No billing info available for license: {}", license.getApplicationNumber());
                }
            } catch (Exception e) {
                log.warn("Failed to enrich billing info for license: {} - {}", 
                        license.getApplicationNumber(), e.getMessage());
                // Continue processing other licenses even if one fails
                // This ensures the API doesn't break if billing service is unavailable
            }
        }
    }
}