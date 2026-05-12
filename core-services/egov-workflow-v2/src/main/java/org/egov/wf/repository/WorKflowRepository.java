package org.egov.wf.repository;


import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.wf.repository.querybuilder.WorkflowQueryBuilder;
import org.egov.wf.repository.rowmapper.WorkflowRowMapper;
import org.egov.wf.service.WorkflowCacheService;
import org.egov.wf.util.WorkflowUtil;
import org.egov.wf.web.models.ProcessInstance;
import org.egov.wf.web.models.ProcessInstanceSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class WorKflowRepository {

    private WorkflowQueryBuilder queryBuilder;

    private JdbcTemplate jdbcTemplate;

    private WorkflowRowMapper rowMapper;

    private WorkflowUtil util;

    private WorkflowCacheService cacheService;


    @Autowired
    public WorKflowRepository(WorkflowQueryBuilder queryBuilder, JdbcTemplate jdbcTemplate,
                               WorkflowRowMapper rowMapper, WorkflowUtil util,
                               WorkflowCacheService cacheService) {
        this.queryBuilder = queryBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
        this.util = util;
        this.cacheService = cacheService;
    }


    /**
     * Executes the search criteria on the db with a cache-aside layer.
     *
     * Simple businessId lookups (no status/assignee/date filters) are served entirely from cache when
     * available. For all other multi-criteria searches the DB result is returned, but any individual
     * process instance that is present in cache is substituted with its cached version so callers
     * always see the most recently cached state.
     */
    public List<ProcessInstance> getProcessInstances(ProcessInstanceSearchCriteria criteria) {
        boolean isSimpleLookup = isSimpleLookup(criteria);

        // For simple single-businessId history lookup, try cache first
        if (isSimpleLookup && Boolean.TRUE.equals(criteria.getHistory())
                && criteria.getBusinessIds().size() == 1) {
            List<ProcessInstance> cached = cacheService.getHistory(
                    criteria.getTenantId(), criteria.getBusinessService(), criteria.getBusinessIds().get(0));
            if (cached != null) {
                log.debug("Cache hit for history [{}/{}]", criteria.getBusinessService(), criteria.getBusinessIds().get(0));
                return cached;
            }
        }

        // For simple latest-state lookups, serve cache hits immediately and query DB only for misses
        if (isSimpleLookup && !Boolean.TRUE.equals(criteria.getHistory())) {
            List<ProcessInstance> cacheHits = new ArrayList<>();
            List<String> missIds = new ArrayList<>();
            for (String businessId : criteria.getBusinessIds()) {
                ProcessInstance cached = cacheService.getLatestProcessInstance(
                        criteria.getTenantId(), criteria.getBusinessService(), businessId);
                if (cached != null) cacheHits.add(cached);
                else missIds.add(businessId);
            }
            if (missIds.isEmpty()) {
                log.debug("Full cache hit for latest [{}/{}]", criteria.getBusinessService(), criteria.getBusinessIds());
                return cacheHits;
            }
            if (!cacheHits.isEmpty()) {
                // Partial hit — query DB only for the missing businessIds
                log.debug("Partial cache hit for latest [{}/{}], fetching {} misses from DB",
                        criteria.getBusinessService(), criteria.getBusinessIds(), missIds.size());
                List<ProcessInstance> dbResults = fetchLatestFromDb(criteria, missIds);
                dbResults.forEach(cacheService::setLatestProcessInstance);
                cacheHits.addAll(dbResults);
                return cacheHits;
            }
            // Full miss — fall through to DB query below
        }

        List<Object> preparedStmtList = new ArrayList<>();
        List<String> ids = getProcessInstanceIds(criteria);
        if (CollectionUtils.isEmpty(ids)) return new LinkedList<>();

        String query = queryBuilder.getProcessInstanceSearchQueryById(ids, preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        log.debug("query for status search: " + query + " params: " + preparedStmtList);

        List<ProcessInstance> result = jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());

        if (CollectionUtils.isEmpty(result)) return result;

        // Populate cache for simple lookups
        if (isSimpleLookup) {
            if (Boolean.TRUE.equals(criteria.getHistory()) && criteria.getBusinessIds().size() == 1) {
                cacheService.setHistory(criteria.getTenantId(), criteria.getBusinessService(),
                        criteria.getBusinessIds().get(0), result);
            } else if (!Boolean.TRUE.equals(criteria.getHistory())) {
                result.forEach(cacheService::setLatestProcessInstance);
            }
        } else {
            // Multi-criteria search: merge cached versions for individual instances where available
            result = cacheService.mergeWithCache(criteria.getTenantId(), criteria.getBusinessService(), result);
        }

        return result;
    }

    /**
     * Returns true when the search is a straightforward lookup by businessService + businessIds
     * with no additional filters that would make cache substitution unsafe.
     */
    private boolean isSimpleLookup(ProcessInstanceSearchCriteria criteria) {
        return criteria.getBusinessService() != null
                && !CollectionUtils.isEmpty(criteria.getBusinessIds())
                && criteria.getStatus() == null
                && criteria.getAssignee() == null
                && criteria.getIds() == null
                && criteria.getFromDate() == null
                && criteria.getToDate() == null;
    }

    /**
     * Queries DB for the given missIds only, using the same tenantId and businessService
     * as the original criteria. Used when a batch lookup has partial cache hits.
     */
    private List<ProcessInstance> fetchLatestFromDb(ProcessInstanceSearchCriteria originalCriteria, List<String> missIds) {
        ProcessInstanceSearchCriteria missCriteria = new ProcessInstanceSearchCriteria();
        missCriteria.setTenantId(originalCriteria.getTenantId());
        missCriteria.setBusinessService(originalCriteria.getBusinessService());
        missCriteria.setBusinessIds(missIds);

        List<Object> preparedStmtList = new ArrayList<>();
        List<String> ids = getProcessInstanceIds(missCriteria);
        if (CollectionUtils.isEmpty(ids)) return new ArrayList<>();

        String query = queryBuilder.getProcessInstanceSearchQueryById(ids, preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, originalCriteria.getTenantId());
        return jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
    }



    /**
     *
     * @param criteria
     * @return
     */
    public List<ProcessInstance> getProcessInstancesForUserInbox(ProcessInstanceSearchCriteria criteria){
        List<Object> preparedStmtList = new ArrayList<>();

        if(CollectionUtils.isEmpty(criteria.getStatus()) && CollectionUtils.isEmpty(criteria.getTenantSpecifiStatus()))
            return new LinkedList<>();

        List<String> ids = getInboxSearchIds(criteria);

        if(CollectionUtils.isEmpty(ids))
            return new LinkedList<>();

        String query = queryBuilder.getProcessInstanceSearchQueryById(ids, preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        log.debug("query for status search: "+query+" params: "+preparedStmtList);
        return jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
    }

    public Integer getProcessInstancesForUserInboxCount(ProcessInstanceSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        criteria.setIsAssignedToMeCount(true);
        String query = queryBuilder.getInboxIdCount(criteria, (ArrayList<Object>) preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        Integer count =  jdbcTemplate.queryForObject(query, Integer.class, preparedStmtList.toArray());
        return count;
    }

    /**
     * Returns the count based on the search criteria
     * @param criteria
     * @return
     */
    public Integer getInboxCount(ProcessInstanceSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getInboxCount(criteria, preparedStmtList,Boolean.FALSE);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        Integer count =  jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
        return count;
    }

    public Integer getProcessInstancesCount(ProcessInstanceSearchCriteria criteria){
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getProcessInstanceCount(criteria, preparedStmtList,Boolean.FALSE);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        return jdbcTemplate.queryForObject(query, Integer.class, preparedStmtList.toArray());
    }

    /**
     * Returns the count based on the search criteria
     * @param criteria
     * @return
     */
    public List getInboxStatusCount(ProcessInstanceSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getInboxCount(criteria, preparedStmtList,Boolean.TRUE);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        log.info(query);
        return jdbcTemplate.queryForList(query, preparedStmtList.toArray());
    }

    public List getProcessInstancesStatusCount(ProcessInstanceSearchCriteria criteria){
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getProcessInstanceCount(criteria, preparedStmtList,Boolean.TRUE);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        return  jdbcTemplate.queryForList(query, preparedStmtList.toArray());
    }



    private List<String> getInboxSearchIds(ProcessInstanceSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        criteria.setIsAssignedToMeCount(false);
        String query = queryBuilder.getInboxIdQuery(criteria,preparedStmtList,true);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }

    private List<String> getProcessInstanceIds(ProcessInstanceSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getProcessInstanceIds(criteria,preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        log.info(query);
        log.info(preparedStmtList.toString());
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }


    public List<String> fetchEscalatedApplicationsBusinessIdsFromDb(RequestInfo requestInfo,ProcessInstanceSearchCriteria criteria) {
        ArrayList<Object> preparedStmtList = new ArrayList<>();

        // 1st step is to fetch businessIds based on the assignee and the module.
        /*

        String query = queryBuilder.getInboxApplicationsBusinessIdsQuery(criteria, preparedStmtList);
        List<String> inboxApplicationsBusinessIds = jdbcTemplate.query(query, preparedStmtList.toArray(), new SingleColumnRowMapper<>(String.class));
        log.info(inboxApplicationsBusinessIds.toString());
        preparedStmtList.clear();

        // (DONE) 2nd step is to fetch businessIds of inbox applications which have been autoEscalated at least once in their wf
        // (DONE) For this step, fetch AUTO_ESCALATION_EMPLOYEES uuids based on role codes by doing a call to user service
        // (PENDING) Also, add the call to mdms service for filtering out states which need to be excluded

        criteria.setBusinessIds(inboxApplicationsBusinessIds);
         */

        String query = queryBuilder.getAutoEscalatedApplicationsBusinessIdsQuery(criteria, preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        List<String> escalatedApplicationsBusinessIds = jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
        preparedStmtList.clear();
        log.info(escalatedApplicationsBusinessIds.toString());
        // 3rd step is to do a simple search on these business ids(DONE IN WORKFLOW SERVICE)

        return escalatedApplicationsBusinessIds;
    }

    public Integer getEscalatedApplicationsCount(RequestInfo requestInfo,ProcessInstanceSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getEscalatedApplicationsCount(requestInfo,criteria, (ArrayList<Object>) preparedStmtList);
        query = util.replaceSchemaPlaceholder(query, criteria.getTenantId());
        Integer count =  jdbcTemplate.queryForObject(query, Integer.class, preparedStmtList.toArray());
        return count;
    }
}