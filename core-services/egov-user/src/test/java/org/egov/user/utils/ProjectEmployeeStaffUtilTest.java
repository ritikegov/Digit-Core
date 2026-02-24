package org.egov.user.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.user.domain.model.boundary.BoundarySearchResponse;
import org.egov.user.domain.model.boundary.BoundaryTypeHierarchyDefinition;
import org.egov.user.domain.model.boundary.BoundaryTypeHierarchyResponse;
import org.egov.user.domain.model.boundary.EnrichedBoundary;
import org.egov.user.domain.model.boundary.HierarchyRelation;
import org.egov.user.domain.model.hrms.Employee;
import org.egov.user.domain.model.hrms.EmployeeResponse;
import org.egov.user.domain.model.hrms.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProjectEmployeeStaffUtilTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private ProjectEmployeeStaffUtil projectEmployeeStaffUtil;

    @Before
    public void setup() {
        projectEmployeeStaffUtil = new ProjectEmployeeStaffUtil(restTemplate, objectMapper);
        ReflectionTestUtils.setField(projectEmployeeStaffUtil, "hrmsServiceHost", "http://hrms-service");
        ReflectionTestUtils.setField(projectEmployeeStaffUtil, "hrmsEmployeeCreateUrl", "/hrms/employee/v1/_create");
        ReflectionTestUtils.setField(projectEmployeeStaffUtil, "boundaryServiceHost", "http://boundary-service");
        ReflectionTestUtils.setField(projectEmployeeStaffUtil, "boundaryHierarchySearchUrl", "/boundary/hierarchy/v1/_search");
        ReflectionTestUtils.setField(projectEmployeeStaffUtil, "boundaryRelationshipsSearchUrl", "/boundary/relationships/v1/_search");
    }

    @Test
    public void testSearchBoundaryHierarchyByTenantId_Success() {
        String tenantId = "pb.amritsar";
        RequestInfo requestInfo = new RequestInfo();

        BoundaryTypeHierarchyDefinition definition = BoundaryTypeHierarchyDefinition.builder()
                .tenantId(tenantId)
                .hierarchyType("REVENUE")
                .build();
        BoundaryTypeHierarchyResponse response = BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(Collections.singletonList(definition))
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(BoundaryTypeHierarchyResponse.class)))
                .thenReturn(response);

        BoundaryTypeHierarchyResponse result = projectEmployeeStaffUtil.searchBoundaryHierarchyByTenantId(tenantId, requestInfo);

        assertNotNull(result);
        assertNotNull(result.getBoundaryHierarchy());
        assertEquals(1, result.getBoundaryHierarchy().size());
        assertEquals("REVENUE", result.getBoundaryHierarchy().get(0).getHierarchyType());
    }

    @Test(expected = CustomException.class)
    public void testSearchBoundaryHierarchyByTenantId_NullResponse_ThrowsCustomException() {
        when(restTemplate.postForObject(anyString(), any(), eq(BoundaryTypeHierarchyResponse.class)))
                .thenReturn(null);

        projectEmployeeStaffUtil.searchBoundaryHierarchyByTenantId("tenant", new RequestInfo());
    }

    @Test
    public void testSearchBoundaryByHierarchyTypeAndTenantId_Success() {
        String hierarchyType = "REVENUE";
        String tenantId = "pb.amritsar";
        RequestInfo requestInfo = new RequestInfo();

        EnrichedBoundary boundary = EnrichedBoundary.builder().code("B1").boundaryType("Locality").build();
        HierarchyRelation relation = HierarchyRelation.builder()
                .tenantId(tenantId)
                .hierarchyType(hierarchyType)
                .boundary(Collections.singletonList(boundary))
                .build();
        BoundarySearchResponse response = BoundarySearchResponse.builder()
                .tenantBoundary(Collections.singletonList(relation))
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(BoundarySearchResponse.class)))
                .thenReturn(response);

        BoundarySearchResponse result = projectEmployeeStaffUtil.searchBoundaryByHierarchyTypeAndTenantId(
                hierarchyType, tenantId, requestInfo);

        assertNotNull(result);
        assertNotNull(result.getTenantBoundary());
        assertEquals(1, result.getTenantBoundary().size());
        assertEquals(1, result.getTenantBoundary().get(0).getBoundary().size());
        assertEquals("B1", result.getTenantBoundary().get(0).getBoundary().get(0).getCode());
    }

    @Test(expected = CustomException.class)
    public void testSearchBoundaryByHierarchyTypeAndTenantId_NullResponse_ThrowsCustomException() {
        when(restTemplate.postForObject(anyString(), any(), eq(BoundarySearchResponse.class)))
                .thenReturn(null);

        projectEmployeeStaffUtil.searchBoundaryByHierarchyTypeAndTenantId("REVENUE", "tenant", new RequestInfo());
    }

    @Test
    public void testCreateEmployeeInHrms_Success() {
        User user = User.builder().name("John").userName("john").build();
        RequestInfo requestInfo = new RequestInfo();

        BoundaryTypeHierarchyDefinition hierarchyDef = BoundaryTypeHierarchyDefinition.builder()
                .hierarchyType("REVENUE")
                .build();
        BoundaryTypeHierarchyResponse hierarchyResponse = BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(Collections.singletonList(hierarchyDef))
                .build();

        EnrichedBoundary boundary = EnrichedBoundary.builder().code("B1").boundaryType("Locality").build();
        HierarchyRelation relation = HierarchyRelation.builder()
                .boundary(Collections.singletonList(boundary))
                .build();
        BoundarySearchResponse boundaryResponse = BoundarySearchResponse.builder()
                .tenantBoundary(Collections.singletonList(relation))
                .build();

        Employee employee = Employee.builder()
                .uuid("E1")
                .user(User.builder().userServiceUuid("U1").build())
                .build();
        EmployeeResponse employeeResponse = EmployeeResponse.builder()
                .employees(Collections.singletonList(employee))
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(BoundaryTypeHierarchyResponse.class)))
                .thenReturn(hierarchyResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(BoundarySearchResponse.class)))
                .thenReturn(boundaryResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(EmployeeResponse.class)))
                .thenReturn(employeeResponse);

        Employee result = projectEmployeeStaffUtil.createEmployeeInHrms(
                user, "PERMANENT", "Designation", "Department", "EMPLOYED",
                System.currentTimeMillis(), "tenant", "oid", requestInfo);

        assertNotNull(result);
        assertEquals("E1", result.getUuid());
        assertEquals("U1", result.getUser().getUserServiceUuid());
    }

    @Test(expected = CustomException.class)
    public void testCreateEmployeeInHrms_NullEmployeeResponse_ThrowsCustomException() {
        User user = User.builder().name("John").userName("john").build();
        RequestInfo requestInfo = new RequestInfo();

        BoundaryTypeHierarchyDefinition hierarchyDef = BoundaryTypeHierarchyDefinition.builder()
                .hierarchyType("REVENUE")
                .build();
        BoundaryTypeHierarchyResponse hierarchyResponse = BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(Collections.singletonList(hierarchyDef))
                .build();

        EnrichedBoundary boundary = EnrichedBoundary.builder().code("B1").boundaryType("Locality").build();
        BoundarySearchResponse boundaryResponse = BoundarySearchResponse.builder()
                .tenantBoundary(Collections.singletonList(
                        HierarchyRelation.builder().boundary(Collections.singletonList(boundary)).build()))
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(BoundaryTypeHierarchyResponse.class)))
                .thenReturn(hierarchyResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(BoundarySearchResponse.class)))
                .thenReturn(boundaryResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(EmployeeResponse.class)))
                .thenReturn(EmployeeResponse.builder().employees(Collections.emptyList()).build());

        projectEmployeeStaffUtil.createEmployeeInHrms(
                user, "PERMANENT", "Designation", "Department", "EMPLOYED",
                System.currentTimeMillis(), "tenant", "oid", requestInfo);
    }

    @Test
    public void testCreateEmployeeAndProjectStaff_Success() {
        User user = User.builder().name("John").userName("john").build();
        RequestInfo requestInfo = new RequestInfo();

        BoundaryTypeHierarchyDefinition hierarchyDef = BoundaryTypeHierarchyDefinition.builder()
                .hierarchyType("REVENUE")
                .build();
        BoundaryTypeHierarchyResponse hierarchyResponse = BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(Collections.singletonList(hierarchyDef))
                .build();

        EnrichedBoundary boundary = EnrichedBoundary.builder().code("B1").boundaryType("Locality").build();
        BoundarySearchResponse boundaryResponse = BoundarySearchResponse.builder()
                .tenantBoundary(Collections.singletonList(
                        HierarchyRelation.builder().boundary(Collections.singletonList(boundary)).build()))
                .build();

        Employee employee = Employee.builder()
                .uuid("E1")
                .user(User.builder().userServiceUuid("U1").build())
                .build();
        EmployeeResponse employeeResponse = EmployeeResponse.builder()
                .employees(Collections.singletonList(employee))
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(BoundaryTypeHierarchyResponse.class)))
                .thenReturn(hierarchyResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(BoundarySearchResponse.class)))
                .thenReturn(boundaryResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(EmployeeResponse.class)))
                .thenReturn(employeeResponse);

        User result = projectEmployeeStaffUtil.createEmployeeAndProjectStaff(
                user, "PERMANENT", "Designation", "Department", "EMPLOYED",
                "tenant", "oid", requestInfo);

        assertNotNull(result);
        assertEquals("U1", result.getUserServiceUuid());
    }

    @Test(expected = CustomException.class)
    public void testCreateEmployeeAndProjectStaff_UserServiceUuidMissing_ThrowsCustomException() {
        User user = User.builder().name("John").userName("john").build();
        RequestInfo requestInfo = new RequestInfo();

        BoundaryTypeHierarchyDefinition hierarchyDef = BoundaryTypeHierarchyDefinition.builder()
                .hierarchyType("REVENUE")
                .build();
        BoundaryTypeHierarchyResponse hierarchyResponse = BoundaryTypeHierarchyResponse.builder()
                .boundaryHierarchy(Collections.singletonList(hierarchyDef))
                .build();

        EnrichedBoundary boundary = EnrichedBoundary.builder().code("B1").boundaryType("Locality").build();
        BoundarySearchResponse boundaryResponse = BoundarySearchResponse.builder()
                .tenantBoundary(Collections.singletonList(
                        HierarchyRelation.builder().boundary(Collections.singletonList(boundary)).build()))
                .build();

        Employee employeeWithoutUuid = Employee.builder()
                .uuid("E1")
                .user(User.builder().userServiceUuid(null).build())
                .build();
        EmployeeResponse employeeResponse = EmployeeResponse.builder()
                .employees(Collections.singletonList(employeeWithoutUuid))
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(BoundaryTypeHierarchyResponse.class)))
                .thenReturn(hierarchyResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(BoundarySearchResponse.class)))
                .thenReturn(boundaryResponse);
        when(restTemplate.postForObject(anyString(), any(), eq(EmployeeResponse.class)))
                .thenReturn(employeeResponse);

        projectEmployeeStaffUtil.createEmployeeAndProjectStaff(
                user, "PERMANENT", "Designation", "Department", "EMPLOYED",
                "tenant", "oid", requestInfo);
    }
}
