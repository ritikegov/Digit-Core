package org.egov.wf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.wf.config.WorkflowConfig;
import org.egov.wf.web.models.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class WorkflowCacheService {

    private static final String LATEST_KEY_PREFIX = "wf:pi:latest:";
    private static final String HISTORY_KEY_PREFIX = "wf:pi:history:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowConfig config;

    @Autowired
    public WorkflowCacheService(RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper,
                                WorkflowConfig config) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    // Hash key groups all businessIds for a given tenant+businessService
    private String latestHashKey(String tenantId, String businessService) {
        return LATEST_KEY_PREFIX + tenantId + ":" + businessService;
    }

    private String historyKey(String tenantId, String businessService, String businessId) {
        return HISTORY_KEY_PREFIX + tenantId + ":" + businessService + ":" + businessId;
    }

    public ProcessInstance getLatestProcessInstance(String tenantId, String businessService, String businessId) {
        try {
            Object cached = redisTemplate.opsForHash().get(latestHashKey(tenantId, businessService), businessId);
            if (cached == null) return null;
            return objectMapper.convertValue(cached, ProcessInstance.class);
        } catch (Exception e) {
            log.warn("Redis read failed for latest [{}/{}]: {}", businessService, businessId, e.getMessage());
            return null;
        }
    }

    public void setLatestProcessInstance(ProcessInstance processInstance) {
        try {
            String hashKey = latestHashKey(processInstance.getTenantId(), processInstance.getBusinessService());
            redisTemplate.opsForHash().put(hashKey, processInstance.getBusinessId(), processInstance);
            redisTemplate.expire(hashKey, config.getProcessInstanceCacheExpiry(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis write failed for latest process instance: {}", e.getMessage());
        }
    }

    public List<ProcessInstance> getHistory(String tenantId, String businessService, String businessId) {
        try {
            Object cached = redisTemplate.opsForValue().get(historyKey(tenantId, businessService, businessId));
            if (cached == null) return null;
            return objectMapper.convertValue(cached, new TypeReference<List<ProcessInstance>>() {});
        } catch (Exception e) {
            log.warn("Redis read failed for history [{}/{}]: {}", businessService, businessId, e.getMessage());
            return null;
        }
    }

    public void setHistory(String tenantId, String businessService, String businessId, List<ProcessInstance> history) {
        try {
            redisTemplate.opsForValue().set(
                    historyKey(tenantId, businessService, businessId),
                    history,
                    config.getProcessInstanceCacheExpiry(),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Redis write failed for history [{}/{}]: {}", businessService, businessId, e.getMessage());
        }
    }

    /**
     * Merges DB results with any fresher data available in cache.
     * For each process instance in dbResults, if a cached version exists it is used; otherwise the DB result stands.
     */
    public List<ProcessInstance> mergeWithCache(String tenantId, String businessService, List<ProcessInstance> dbResults) {
        if (CollectionUtils.isEmpty(dbResults) || businessService == null) return dbResults;
        for (int i = 0; i < dbResults.size(); i++) {
            ProcessInstance cached = getLatestProcessInstance(tenantId, businessService, dbResults.get(i).getBusinessId());
            if (cached != null) {
                dbResults.set(i, cached);
            }
        }
        return dbResults;
    }

    /**
     * Writes the post-transition state of each process instance into the latest cache and
     * deletes its history cache entry. Must be called synchronously after updateStatus.
     *
     * Simple deletion is insufficient: the DB write is async (Kafka persister), so a search
     * arriving after the transition but before the Kafka consumer commits would miss the cache,
     * read stale DB state, re-populate the cache with pre-transition data, and return wrong
     * nextActions — causing callers to retry an already-completed transition.
     *
     * By writing the post-transition instance (state = resultantState) directly to cache here,
     * any concurrent search gets a cache hit with the correct new state during the Kafka window.
     */
    public void updateOnTransition(List<ProcessInstance> processInstances) {
        if (CollectionUtils.isEmpty(processInstances)) return;
        for (ProcessInstance pi : processInstances) {
            try {
                setLatestProcessInstance(pi);
                redisTemplate.delete(historyKey(pi.getTenantId(), pi.getBusinessService(), pi.getBusinessId()));
            } catch (Exception e) {
                log.warn("Redis update failed on transition for [{}/{}]: {}", pi.getBusinessService(), pi.getBusinessId(), e.getMessage());
            }
        }
    }
}
