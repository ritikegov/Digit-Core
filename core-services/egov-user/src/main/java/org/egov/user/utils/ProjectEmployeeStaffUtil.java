package org.egov.user.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.user.domain.model.boundary.*;
import org.egov.user.domain.model.hrms.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Utility class for creating employees in HRMS and project staff mappings.
 * This utility searches for a project by name and boundary code, creates an
 * employee
 * in egov-hrms with boundary details from the project, and then creates a
 * project staff mapping.
 */
@Component
@Slf4j
public class ProjectEmployeeStaffUtil {

        /** Exception codes (used in CustomException). */
        private static final String CUSTOM_EXCEPTION_BOUNDARY_HIERARCHY = "BOUNDARY_HIERARCHY_SEARCH_FAILED";
        private static final String CUSTOM_EXCEPTION_BOUNDARY_RELATIONSHIPS = "BOUNDARY_RELATIONSHIPS_SEARCH_FAILED";
        private static final String CUSTOM_EXCEPTION_BOUNDARY_HIERARCHY_EMPTY = "BOUNDARY_HIERARCHY_EMPTY";
        private static final String CUSTOM_EXCEPTION_BOUNDARY_TENANT_EMPTY = "BOUNDARY_TENANT_EMPTY";
        private static final String CUSTOM_EXCEPTION_BOUNDARY_LIST_EMPTY = "BOUNDARY_LIST_EMPTY";
        private static final String CUSTOM_EXCEPTION_EMPLOYEE_CREATION_FAILED = "EMPLOYEE_CREATION_FAILED";
        private static final String CUSTOM_EXCEPTION_USER_SERVICE_UUID_MISSING = "USER_SERVICE_UUID_MISSING";
        private static final String CUSTOM_EXCEPTION_HTTP_CLIENT_ERROR = "HTTP_CLIENT_ERROR";
        private static final String CUSTOM_EXCEPTION_SERVICE_REQUEST_CLIENT_ERROR = "SERVICE_REQUEST_CLIENT_ERROR";
        /** Query/param keys used in code. */
        private static final String PARAM_TENANT_ID = "tenantId";
        private static final String PARAM_HIERARCHY_TYPE = "hierarchyType";

        private final RestTemplate restTemplate;

        @Value("${egov.hrms.host}")
        private String hrmsServiceHost;

        @Value("${egov.hrms.employee.create.url}")
        private String hrmsEmployeeCreateUrl;

        @Value("${egov.boundary.host}")
        private String boundaryServiceHost;

        @Value("${egov.boundary.hierarchy.search.url}")
        private String boundaryHierarchySearchUrl;

        @Value("${egov.boundary.relationships.search.url}")
        private String boundaryRelationshipsSearchUrl;

        @Autowired
        public ProjectEmployeeStaffUtil(RestTemplate restTemplate) {
                this.restTemplate = restTemplate;
        }

        /**
         * Searches boundary hierarchy definitions for the given tenant.
         * Calls boundary-service hierarchy definition search API.
         *
         * @param tenantId    The tenant ID
         * @param requestInfo Request info for the API call
         * @return BoundaryTypeHierarchyResponse containing list of hierarchy definitions (may be empty)
         */
        public BoundaryTypeHierarchyResponse searchBoundaryHierarchyByTenantId(String tenantId, RequestInfo requestInfo) {
                log.info("Searching for boundary hierarchy by tenantId: {}", tenantId);
                BoundaryTypeHierarchySearchCriteria criteria = BoundaryTypeHierarchySearchCriteria.builder()
                        .tenantId(tenantId)
                        .limit(1)
                        .offset(0)
                        .build();
                BoundaryTypeHierarchySearchRequest request = BoundaryTypeHierarchySearchRequest.builder()
                        .requestInfo(requestInfo)
                        .boundaryTypeHierarchySearchCriteria(criteria)
                        .build();
                StringBuilder uri = new StringBuilder()
                        .append(boundaryServiceHost)
                        .append(boundaryHierarchySearchUrl);
                BoundaryTypeHierarchyResponse response = fetchResult(uri, request, BoundaryTypeHierarchyResponse.class);
                if (response == null) {
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_HIERARCHY, "Boundary hierarchy search returned null");
                }
                return response;
        }

        /**
         * Searches boundary relationships by hierarchy type and tenant.
         * Calls boundary-service relationship search API (criteria as query params, RequestInfo in body).
         *
         * @param hierarchyType The hierarchy type (e.g. "REVENUE", "ADMIN")
         * @param tenantId      The tenant ID
         * @param requestInfo   Request info for the API call
         * @return BoundarySearchResponse containing TenantBoundary list (may be empty)
         */
        public BoundarySearchResponse searchBoundaryByHierarchyTypeAndTenantId(String hierarchyType, String tenantId,
                        RequestInfo requestInfo) {
                log.info("Searching for boundaries by hierarchyType: {} and tenantId: {}", hierarchyType, tenantId);
                String url = UriComponentsBuilder.fromHttpUrl(boundaryServiceHost + boundaryRelationshipsSearchUrl)
                        .queryParam(PARAM_TENANT_ID, tenantId)
                        .queryParam(PARAM_HIERARCHY_TYPE, hierarchyType)
                        .build()
                        .toUriString();
                BoundarySearchResponse response = fetchResult(new StringBuilder(url), requestInfo,
                        BoundarySearchResponse.class);
                if (response == null) {
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_RELATIONSHIPS, "Boundary relationships search returned null");
                }
                return response;
        }

        private String getHierarchyTypeOrThrow(BoundaryTypeHierarchyResponse response, String tenantId) {
                List<org.egov.user.domain.model.boundary.BoundaryTypeHierarchyDefinition> hierarchy = response == null
                        ? null
                        : response.getBoundaryHierarchy();
                if (CollectionUtils.isEmpty(hierarchy)) {
                        log.warn("Boundary hierarchy empty for tenantId: {}", tenantId);
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_HIERARCHY_EMPTY,
                                "No boundary hierarchy found for tenant " + PARAM_TENANT_ID + "=" + tenantId);
                }
                String type = hierarchy.get(0).getHierarchyType();
                if (type == null || type.trim().isEmpty()) {
                        log.warn("Hierarchy type null or empty for tenantId: {}", tenantId);
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_HIERARCHY_EMPTY,
                                "No boundary hierarchy found for tenant " + PARAM_TENANT_ID + "=" + tenantId);
                }
                return type.trim();
        }

        private BoundaryCodeAndType getBoundaryCodeAndTypeOrThrow(BoundarySearchResponse response,
                        String hierarchyType, String tenantId) {
                List<HierarchyRelation> tenantBoundary = response == null ? null : response.getTenantBoundary();
                if (CollectionUtils.isEmpty(tenantBoundary)) {
                        log.warn("Tenant boundary empty for hierarchyType: {}, tenantId: {}", hierarchyType, tenantId);
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_TENANT_EMPTY,
                                "No tenant boundary found for hierarchy type and tenant " + PARAM_HIERARCHY_TYPE + "=" + hierarchyType
                                        + ", " + PARAM_TENANT_ID + "=" + tenantId);
                }
                List<org.egov.user.domain.model.boundary.EnrichedBoundary> boundary = tenantBoundary.get(0).getBoundary();
                if (CollectionUtils.isEmpty(boundary)) {
                        log.warn("Boundary list empty for hierarchyType: {}, tenantId: {}", hierarchyType, tenantId);
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_LIST_EMPTY,
                                "No boundary found in tenant boundary " + PARAM_HIERARCHY_TYPE + "=" + hierarchyType
                                        + ", " + PARAM_TENANT_ID + "=" + tenantId);
                }
                org.egov.user.domain.model.boundary.EnrichedBoundary first = boundary.get(0);
                String code = first.getCode();
                String boundaryType = first.getBoundaryType();
                if (code == null || code.trim().isEmpty()) {
                        throw new CustomException(CUSTOM_EXCEPTION_BOUNDARY_LIST_EMPTY,
                                "Boundary code missing for " + PARAM_TENANT_ID + "=" + tenantId);
                }
                return new BoundaryCodeAndType(code.trim(), boundaryType != null ? boundaryType.trim() : null);
        }

        private static final class BoundaryCodeAndType {
                private final String code;
                private final String boundaryType;

                BoundaryCodeAndType(String code, String boundaryType) {
                        this.code = code;
                        this.boundaryType = boundaryType;
                }

                String getCode() {
                        return code;
                }

                String getBoundaryType() {
                        return boundaryType;
                }
        }

        /**
         * Creates an employee in egov-hrms with boundary details from the project.
         *
         * @param user              The user object containing user details
         * @param employeeType      The type of employee (PERMANENT, TEMPORARY, etc.)
         * @param designation       The designation for the employee assignment
         * @param department        The department for the employee assignment
         * @param employeeStatus    The status of the employee
         * @param dateOfAppointment The date of appointment for the employee
         * @param tenantId          The tenant ID
         * @param requestInfo       The request info for authentication and tracking
         * @return The created Employee object with userServiceUuid
         * @throws CustomException if employee creation fails
         */
        public Employee createEmployeeInHrms(User user,
                        String employeeType, String designation, String department,
                        String employeeStatus, Long dateOfAppointment,
                        String tenantId, String createdBy, RequestInfo requestInfo) {
                log.info("Creating employee in HRMS for user: {}", user.getName());
                BoundaryTypeHierarchyResponse boundaryTypeHierarchyResponse = searchBoundaryHierarchyByTenantId(tenantId, requestInfo);
                String hierarchyType = getHierarchyTypeOrThrow(boundaryTypeHierarchyResponse, tenantId);
                BoundarySearchResponse boundarySearchResponse = searchBoundaryByHierarchyTypeAndTenantId(hierarchyType, tenantId, requestInfo);
                BoundaryCodeAndType boundaryCodeAndType = getBoundaryCodeAndTypeOrThrow(boundarySearchResponse, hierarchyType, tenantId);
                String boundaryCode = boundaryCodeAndType.getCode();
                String boundaryType = boundaryCodeAndType.getBoundaryType();

                // Create jurisdiction from project boundary
                Jurisdiction jurisdiction = Jurisdiction.builder()
                                .hierarchy(hierarchyType)
                                .boundary(boundaryCode)
                                .boundaryType(boundaryType)
                                .tenantId(tenantId)
                                .isActive(true)
                                .build();

                // Create assignment
                Assignment assignment = Assignment.builder()
                                .designation(designation)
                                .department(department)
                                .fromDate(System.currentTimeMillis())
                                .isCurrentAssignment(true)
                                .build();

                // Create audit details
                AuditDetails auditDetails = AuditDetails.builder()
                                .createdBy(createdBy)
                                .createdDate(System.currentTimeMillis())
                                .lastModifiedBy(createdBy)
                                .lastModifiedDate(System.currentTimeMillis())
                                .build();

                // Enrich HRMS user with audit details
                user.setCreatedBy(createdBy);
                user.setCreatedDate(System.currentTimeMillis());
                user.setLastModifiedBy(createdBy);
                user.setLastModifiedDate(System.currentTimeMillis());

                // Create employee
                Employee employee = Employee.builder()
                                .employeeType(employeeType)
                                .employeeStatus(employeeStatus)
                                .dateOfAppointment(dateOfAppointment)
                                .tenantId(tenantId)
                                .jurisdictions(Collections.singletonList(jurisdiction))
                                .assignments(Collections.singletonList(assignment))
                                .user(user)
                                .auditDetails(auditDetails)
                        .code(user.getUserName())
                                .build();

                // Create employee request
                EmployeeRequest employeeRequest = EmployeeRequest.builder()
                                .requestInfo(requestInfo)
                                .employees(Collections.singletonList(employee))
                                .build();

                // Build the URI
                StringBuilder uri = new StringBuilder();
                uri.append(hrmsServiceHost)
                                .append(hrmsEmployeeCreateUrl);

                // Make the API call
                EmployeeResponse response = fetchResult(uri, employeeRequest, EmployeeResponse.class);

                // Validate response
                if (response == null || CollectionUtils.isEmpty(response.getEmployees())) {
                        log.error("Failed to create employee in HRMS for user: {}", user.getName());
                        throw new CustomException(CUSTOM_EXCEPTION_EMPLOYEE_CREATION_FAILED,
                                        "Failed to create employee in HRMS service");
                }

                Employee createdEmployee = response.getEmployees().get(0);
                log.info("Successfully created employee in HRMS with UUID: {}", createdEmployee.getUuid());
                return createdEmployee;
        }

        /**
         * Complete workflow: Create employee in HRMS and return the created user.
         * This is a convenience method that orchestrates the flow. Employee creation
         * in HRMS is not rolled back if a subsequent step (e.g. UUID validation) fails;
         * no compensation (void/delete) is performed. Local DB is not used here, so
         * {@code @Transactional} does not apply.
         *
         * @param user           The user object containing user details
         * @param employeeType   The type of employee (PERMANENT, TEMPORARY, etc.)
         * @param designation    The designation for the employee assignment
         * @param department     The department for the employee assignment
         * @param employeeStatus The status of the employee
         * @param tenantId       The tenant ID
         * @param createdBy      The creator identifier
         * @param requestInfo    The request info for authentication and tracking
         * @return The created user from the HRMS employee response
         * @throws CustomException if any step in the workflow fails
         */
        public User createEmployeeAndProjectStaff(
                        User user,
                        String employeeType, String designation,
                        String department, String employeeStatus,
                        String tenantId, String createdBy,
                        RequestInfo requestInfo) {


                // Create employee in HRMS
                Employee employee = createEmployeeInHrms(user, employeeType,
                                designation, department, employeeStatus, System.currentTimeMillis(),
                                tenantId, createdBy, requestInfo);

                String userServiceUuid = employee.getUser().getUserServiceUuid();
                if (userServiceUuid == null || userServiceUuid.isEmpty()) {
                        throw new CustomException(CUSTOM_EXCEPTION_USER_SERVICE_UUID_MISSING,
                                        "User service UUID is missing from HRMS employee response");
                }

                log.info("Successfully completed workflow for user: {}", user.getName());
                return employee.getUser();
        }

        public <T> T fetchResult(StringBuilder uri, Object request, Class<T> clazz) {
                T response;
                try {
                        // Perform HTTP POST request and receive the response
                        response = restTemplate.postForObject(uri.toString(), request, clazz);
                } catch (HttpClientErrorException e) {
                        // Handle HTTP client errors
                        throw new CustomException(CUSTOM_EXCEPTION_HTTP_CLIENT_ERROR,
                                        String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
                } catch (Exception exception) {
                        throw new CustomException(CUSTOM_EXCEPTION_SERVICE_REQUEST_CLIENT_ERROR,
                                        exception.getMessage());
                }
                return response;
        }
}