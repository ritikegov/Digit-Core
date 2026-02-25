package org.egov.infra.mdms.repository.impl;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.infra.mdms.config.ApplicationConfig;
import org.egov.infra.mdms.model.Mdms;
import org.egov.infra.mdms.model.MdmsCriteria;
import org.egov.infra.mdms.model.MdmsCriteriaV2;
import org.egov.infra.mdms.model.MdmsRequest;
import org.egov.infra.mdms.producer.Producer;
import org.egov.infra.mdms.repository.MdmsDataRepository;
import org.egov.infra.mdms.repository.querybuilder.MdmsDataQueryBuilder;
import org.egov.infra.mdms.repository.querybuilder.MdmsDataQueryBuilderV2;
import org.egov.infra.mdms.repository.rowmapper.MdmsDataRowMapper;
import org.egov.infra.mdms.repository.rowmapper.MdmsDataRowMapperV2;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.egov.infra.mdms.errors.ErrorCodes.INVALID_TENANT_ID_ERR_CODE;
import static org.egov.infra.mdms.errors.ErrorCodes.TENANT_LIST_FETCH_ERR_CODE;
import static org.egov.infra.mdms.errors.ErrorCodes.TENANT_LIST_FETCH_ERR_MSG;
import static org.egov.infra.mdms.utils.MDMSConstants.EG_MDMS_DATA_TABLE;
import static org.egov.infra.mdms.utils.MDMSConstants.INFO_SCHEMA_TABLES;
import static org.egov.infra.mdms.utils.MDMSConstants.SCHEMA_INFO_SCHEMA;
import static org.egov.infra.mdms.utils.MDMSConstants.SCHEMA_PG_CATALOG;
import static org.egov.infra.mdms.utils.MDMSConstants.TABLE_NAME_COLUMN;
import static org.egov.infra.mdms.utils.MDMSConstants.TABLE_SCHEMA_COLUMN;
import static org.egov.infra.mdms.utils.MDMSConstants.DEFAULT_TENANT_ID_FOR_SCHEMA_REPLACE;
import static org.egov.infra.mdms.utils.MDMSConstants.TENANTID_COLUMN;

@Repository
@Slf4j
public class MdmsDataRepositoryImpl implements MdmsDataRepository {

    private final Producer producer;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationConfig applicationConfig;
    private final MdmsDataQueryBuilder mdmsDataQueryBuilder;
    private final MdmsDataQueryBuilderV2 mdmsDataQueryBuilderV2;
    private final MdmsDataRowMapperV2 mdmsDataRowMapperV2;
    private final MdmsDataRowMapper mdmsDataRowMapper;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    /**
     * Constructs an instance of MdmsDataRepositoryImpl with the necessary dependencies.
     *
     * @param producer The producer component responsible for Kafka message publication.
     * @param jdbcTemplate The JdbcTemplate for executing database operations.
     * @param applicationConfig The configuration object containing application-specific properties.
     * @param mdmsDataQueryBuilder The query builder used for constructing MDMS data search queries.
     * @param mdmsDataRowMapperV2 The row mapper implementation for mapping result sets to MDMS data objects (version 2).
     * @param mdmsDataQueryBuilderV2 The query builder used for constructing MDMS data search queries (version 2).
     * @param mdmsDataRowMapper The row mapper implementation for mapping result sets to MDMS data objects.
     * @param multiStateInstanceUtil Utility for handling multi-state-specific logic and configurations.
     */
    @Autowired
    public MdmsDataRepositoryImpl(Producer producer, JdbcTemplate jdbcTemplate,
                                  ApplicationConfig applicationConfig, MdmsDataQueryBuilder mdmsDataQueryBuilder,
                                  MdmsDataRowMapperV2 mdmsDataRowMapperV2,
                                  MdmsDataQueryBuilderV2 mdmsDataQueryBuilderV2,
                                  MdmsDataRowMapper mdmsDataRowMapper, MultiStateInstanceUtil multiStateInstanceUtil) {
        this.producer = producer;
        this.jdbcTemplate = jdbcTemplate;
        this.applicationConfig = applicationConfig;
        this.mdmsDataQueryBuilder = mdmsDataQueryBuilder;
        this.mdmsDataRowMapper = mdmsDataRowMapper;
        this.mdmsDataRowMapperV2 = mdmsDataRowMapperV2;
        this.mdmsDataQueryBuilderV2 = mdmsDataQueryBuilderV2;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    /**
     * @param mdmsRequest
     */
    @Override
    public void create(MdmsRequest mdmsRequest) {
        producer.push(mdmsRequest.getMdms().getTenantId(), applicationConfig.getSaveMdmsDataTopicName(), mdmsRequest);
    }

    /**
     * @param mdmsRequest
     */
    @Override
    public void update(MdmsRequest mdmsRequest) {
        producer.push(mdmsRequest.getMdms().getTenantId(), applicationConfig.getUpdateMdmsDataTopicName(), mdmsRequest);
    }

    /**
     * @param mdmsCriteriaV2
     * @return
     */
    @Override
    public List<Mdms> searchV2(MdmsCriteriaV2 mdmsCriteriaV2) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = mdmsDataQueryBuilderV2.getMdmsDataSearchQuery(mdmsCriteriaV2, preparedStmtList);
        try {
            // Replaced schema placeholder in the query with tenant specific schema name
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, mdmsCriteriaV2.getTenantId());
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        log.info("Mdms Data search query: {}", query);
        return jdbcTemplate.query(query, preparedStmtList.toArray(), mdmsDataRowMapperV2);
    }

    /**
     * @param mdmsCriteria
     * @return
     */
    @Override
    public Map<String, Map<String, JSONArray>> search(MdmsCriteria mdmsCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = mdmsDataQueryBuilder.getMdmsDataSearchQuery(mdmsCriteria, preparedStmtList);
        try {
            // Replaced schema placeholder in the query with tenant specific schema name
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, mdmsCriteria.getTenantId());
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        log.info("Mdms Data search query: {}", query);
        return jdbcTemplate.query(query, preparedStmtList.toArray(), mdmsDataRowMapper);
    }

    @Override
    public List<String> findDistinctTenantIds() {
        try {
            if (Boolean.FALSE.equals(multiStateInstanceUtil.getIsEnvironmentCentralInstance())) {
                return findDistinctTenantIdsSingleSchema();
            }
            return findDistinctTenantIdsCentralInstance();
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        } catch (DataAccessException e) {
            log.error("Error fetching distinct tenant ids", e);
            throw new CustomException(TENANT_LIST_FETCH_ERR_CODE, TENANT_LIST_FETCH_ERR_MSG);
        }
    }

    private List<String> findDistinctTenantIdsSingleSchema() throws InvalidTenantIdException {
        String query = mdmsDataQueryBuilderV2.getDistinctTenantIdsQuery();
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, DEFAULT_TENANT_ID_FOR_SCHEMA_REPLACE);
        log.debug("Distinct tenant ids query (single schema): {}", query);
        List<String> tenantIds = jdbcTemplate.query(query, (rs, rowNum) -> rs.getString(TENANTID_COLUMN));
        return tenantIds != null ? tenantIds : new ArrayList<>();
    }

    private List<String> findDistinctTenantIdsCentralInstance() {
        String schemaListQuery = "SELECT DISTINCT " + TABLE_SCHEMA_COLUMN + " FROM " + INFO_SCHEMA_TABLES
                + " WHERE " + TABLE_NAME_COLUMN + " = ? AND " + TABLE_SCHEMA_COLUMN + " NOT IN (?, ?)";
        List<String> schemas = jdbcTemplate.query(schemaListQuery,
                new Object[]{EG_MDMS_DATA_TABLE, SCHEMA_PG_CATALOG, SCHEMA_INFO_SCHEMA},
                (rs, rowNum) -> rs.getString(TABLE_SCHEMA_COLUMN));

        Set<String> allTenantIds = new LinkedHashSet<>();
        String baseQuery = mdmsDataQueryBuilderV2.getDistinctTenantIdsQuery();

        for (String schemaName : schemas) {
            String query = baseQuery.replaceAll("(?i)" + Pattern.quote(SCHEMA_REPLACE_STRING), schemaName);
            log.debug("Distinct tenant ids query for schema {}: {}", schemaName, query);
            List<String> tenantIds = jdbcTemplate.query(query, (rs, rowNum) -> rs.getString(TENANTID_COLUMN));
            if (tenantIds != null) {
                allTenantIds.addAll(tenantIds);
            }
        }
        return new ArrayList<>(allTenantIds);
    }
}
