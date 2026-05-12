package org.egov.wf.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.wf.config.WorkflowConfig;
import org.egov.wf.web.models.ProcessInstance;
import org.egov.wf.web.models.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ContextConfiguration(classes = {WorkflowCacheService.class})
@ExtendWith(SpringExtension.class)
class WorkflowCacheServiceTest {

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowConfig workflowConfig;

    @Autowired
    private WorkflowCacheService workflowCacheService;

    private static final String TENANT = "pb";
    private static final String BUSINESS_SERVICE = "TL";
    private static final String BUSINESS_ID = "TL-2024-001";
    private static final String EXPECTED_LATEST_KEY = "wf:pi:latest:" + TENANT + ":" + BUSINESS_SERVICE + ":" + BUSINESS_ID;
    private static final String EXPECTED_HISTORY_KEY = "wf:pi:history:" + TENANT + ":" + BUSINESS_SERVICE + ":" + BUSINESS_ID;

    private ProcessInstance buildProcessInstance(String stateValue) {
        ProcessInstance pi = new ProcessInstance();
        pi.setTenantId(TENANT);
        pi.setBusinessService(BUSINESS_SERVICE);
        pi.setBusinessId(BUSINESS_ID);
        State state = new State();
        state.setState(stateValue);
        pi.setState(state);
        return pi;
    }

    // -------------------------------------------------------------------------
    // updateOnTransition
    // -------------------------------------------------------------------------

    @Test
    void testUpdateOnTransition_writesNewStateToCache() {
        ProcessInstance pi = buildProcessInstance("UNDER_REVIEW");
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(workflowConfig.getProcessInstanceCacheExpiry()).thenReturn(60L);

        workflowCacheService.updateOnTransition(Collections.singletonList(pi));

        verify(valueOps).set(eq(EXPECTED_LATEST_KEY), eq(pi), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void testUpdateOnTransition_deletesHistoryCache() {
        ProcessInstance pi = buildProcessInstance("UNDER_REVIEW");
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(workflowConfig.getProcessInstanceCacheExpiry()).thenReturn(60L);

        workflowCacheService.updateOnTransition(Collections.singletonList(pi));

        verify(redisTemplate).delete(EXPECTED_HISTORY_KEY);
    }

    @Test
    void testUpdateOnTransition_emptyList_doesNothing() {
        workflowCacheService.updateOnTransition(new ArrayList<>());

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void testUpdateOnTransition_nullList_doesNothing() {
        workflowCacheService.updateOnTransition(null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void testUpdateOnTransition_redisFailure_doesNotThrow() {
        ProcessInstance pi = buildProcessInstance("UNDER_REVIEW");
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Redis down")).when(valueOps).set(any(), any(), anyLong(), any());

        assertDoesNotThrow(() -> workflowCacheService.updateOnTransition(Collections.singletonList(pi)));
    }

    @Test
    void testUpdateOnTransition_multipleInstances_writesEach() {
        ProcessInstance pi1 = buildProcessInstance("UNDER_REVIEW");
        ProcessInstance pi2 = new ProcessInstance();
        pi2.setTenantId(TENANT);
        pi2.setBusinessService(BUSINESS_SERVICE);
        pi2.setBusinessId("TL-2024-002");
        State s = new State();
        s.setState("APPROVED");
        pi2.setState(s);

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(workflowConfig.getProcessInstanceCacheExpiry()).thenReturn(60L);

        workflowCacheService.updateOnTransition(List.of(pi1, pi2));

        verify(valueOps).set(eq(EXPECTED_LATEST_KEY), eq(pi1), eq(60L), eq(TimeUnit.SECONDS));
        verify(valueOps).set(eq("wf:pi:latest:" + TENANT + ":" + BUSINESS_SERVICE + ":TL-2024-002"), eq(pi2), eq(60L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete(EXPECTED_HISTORY_KEY);
        verify(redisTemplate).delete("wf:pi:history:" + TENANT + ":" + BUSINESS_SERVICE + ":TL-2024-002");
    }

    // -------------------------------------------------------------------------
    // getLatestProcessInstance
    // -------------------------------------------------------------------------

    @Test
    void testGetLatestProcessInstance_cacheHit_returnsInstance() {
        ProcessInstance expected = buildProcessInstance("INITIATED");
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_LATEST_KEY)).thenReturn(expected);
        when(objectMapper.convertValue(expected, ProcessInstance.class)).thenReturn(expected);

        ProcessInstance result = workflowCacheService.getLatestProcessInstance(TENANT, BUSINESS_SERVICE, BUSINESS_ID);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    void testGetLatestProcessInstance_cacheMiss_returnsNull() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        assertNull(workflowCacheService.getLatestProcessInstance(TENANT, BUSINESS_SERVICE, BUSINESS_ID));
    }

    @Test
    void testGetLatestProcessInstance_redisFailure_returnsNull() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new RuntimeException("Redis down"));

        assertNull(workflowCacheService.getLatestProcessInstance(TENANT, BUSINESS_SERVICE, BUSINESS_ID));
    }

    // -------------------------------------------------------------------------
    // setLatestProcessInstance
    // -------------------------------------------------------------------------

    @Test
    void testSetLatestProcessInstance_writesToKeyWithExpiry() {
        ProcessInstance pi = buildProcessInstance("INITIATED");
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(workflowConfig.getProcessInstanceCacheExpiry()).thenReturn(120L);

        workflowCacheService.setLatestProcessInstance(pi);

        verify(valueOps).set(EXPECTED_LATEST_KEY, pi, 120L, TimeUnit.SECONDS);
        verify(redisTemplate, never()).expire(any(), anyLong(), any());
    }

    @Test
    void testSetLatestProcessInstance_redisFailure_throws() {
        ProcessInstance pi = buildProcessInstance("INITIATED");
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Redis down")).when(valueOps).set(any(), any(), anyLong(), any());

        assertThrows(RuntimeException.class, () -> workflowCacheService.setLatestProcessInstance(pi));
    }

    // -------------------------------------------------------------------------
    // mergeWithCache
    // -------------------------------------------------------------------------

    @Test
    void testMergeWithCache_replacesDbResultWithCachedVersion() {
        ProcessInstance dbInstance = buildProcessInstance("INITIATED");
        ProcessInstance cachedInstance = buildProcessInstance("UNDER_REVIEW");

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_LATEST_KEY)).thenReturn(cachedInstance);
        when(objectMapper.convertValue(cachedInstance, ProcessInstance.class)).thenReturn(cachedInstance);

        List<ProcessInstance> dbResults = new ArrayList<>(Collections.singletonList(dbInstance));
        List<ProcessInstance> result = workflowCacheService.mergeWithCache(TENANT, BUSINESS_SERVICE, dbResults);

        assertEquals(1, result.size());
        assertEquals("UNDER_REVIEW", result.get(0).getState().getState());
    }

    @Test
    void testMergeWithCache_keepsDbResultWhenNoCacheEntry() {
        ProcessInstance dbInstance = buildProcessInstance("INITIATED");

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        List<ProcessInstance> dbResults = new ArrayList<>(Collections.singletonList(dbInstance));
        List<ProcessInstance> result = workflowCacheService.mergeWithCache(TENANT, BUSINESS_SERVICE, dbResults);

        assertEquals(1, result.size());
        assertEquals("INITIATED", result.get(0).getState().getState());
    }

    @Test
    void testMergeWithCache_nullCriteriaBusinessService_usesInstanceBusinessService() {
        // When criteria businessService is null, mergeWithCache falls back to the instance's own field
        ProcessInstance dbInstance = buildProcessInstance("INITIATED");
        ProcessInstance cachedInstance = buildProcessInstance("UNDER_REVIEW");

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_LATEST_KEY)).thenReturn(cachedInstance);
        when(objectMapper.convertValue(cachedInstance, ProcessInstance.class)).thenReturn(cachedInstance);

        List<ProcessInstance> dbResults = new ArrayList<>(Collections.singletonList(dbInstance));
        List<ProcessInstance> result = workflowCacheService.mergeWithCache(TENANT, null, dbResults);

        assertEquals(1, result.size());
        assertEquals("UNDER_REVIEW", result.get(0).getState().getState());
    }

    @Test
    void testMergeWithCache_nullCriteriaAndNullInstanceBusinessService_skipsEntry() {
        ProcessInstance dbInstance = new ProcessInstance();
        dbInstance.setTenantId(TENANT);
        dbInstance.setBusinessId(BUSINESS_ID);
        // businessService null on both criteria and instance

        List<ProcessInstance> dbResults = new ArrayList<>(Collections.singletonList(dbInstance));
        List<ProcessInstance> result = workflowCacheService.mergeWithCache(TENANT, null, dbResults);

        assertSame(dbInstance, result.get(0));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void testMergeWithCache_emptyDbResults_returnsEmptyUnchanged() {
        List<ProcessInstance> result = workflowCacheService.mergeWithCache(TENANT, BUSINESS_SERVICE, new ArrayList<>());

        assertTrue(result.isEmpty());
        verifyNoInteractions(redisTemplate);
    }

    // -------------------------------------------------------------------------
    // acquireTransitionLock / releaseTransitionLock
    // -------------------------------------------------------------------------

    @Test
    void testAcquireTransitionLock_returnsTrue_whenLockNotHeld() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(true);

        assertTrue(workflowCacheService.acquireTransitionLock(TENANT, BUSINESS_SERVICE, BUSINESS_ID));
    }

    @Test
    void testAcquireTransitionLock_returnsFalse_whenLockAlreadyHeld() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(false);

        assertFalse(workflowCacheService.acquireTransitionLock(TENANT, BUSINESS_SERVICE, BUSINESS_ID));
    }

    @Test
    void testAcquireTransitionLock_returnsTrue_onRedisFailure_degradedMode() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), anyLong(), any())).thenThrow(new RuntimeException("Redis down"));

        assertTrue(workflowCacheService.acquireTransitionLock(TENANT, BUSINESS_SERVICE, BUSINESS_ID));
    }

    @Test
    void testReleaseTransitionLock_deletesLockKey() {
        String expectedLockKey = "wf:lock:" + TENANT + ":" + BUSINESS_SERVICE + ":" + BUSINESS_ID;

        workflowCacheService.releaseTransitionLock(TENANT, BUSINESS_SERVICE, BUSINESS_ID);

        verify(redisTemplate).delete(expectedLockKey);
    }

    @Test
    void testReleaseTransitionLock_redisFailure_doesNotThrow() {
        when(redisTemplate.delete(any(String.class))).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> workflowCacheService.releaseTransitionLock(TENANT, BUSINESS_SERVICE, BUSINESS_ID));
    }
}