package org.egov.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.domain.model.MobileValidationConfig;
import org.egov.web.contract.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Slf4j
public class MdmsRepository {

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsSearchEndpoint;

    @Value("${egov.mdms.module.name:common-masters}")
    private String mdmsModuleName;

    @Value("${egov.mdms.master.name:UserValidation}")
    private String mdmsMasterName;

    @Autowired
    private RestCallRepository restCallRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public List<MobileValidationConfig> fetchMobileValidationConfigs(String tenantId, RequestInfo requestInfo) {
        String stateTenantId = tenantId.split("\\.")[0];
        Map<String, Object> request = buildMdmsRequest(stateTenantId, requestInfo);
        StringBuilder url = new StringBuilder(mdmsHost).append(mdmsSearchEndpoint);

        Optional<Object> response = restCallRepository.fetchResult(url, request);
        if (response.isEmpty()) {
            log.warn("No MDMS response for tenantId: {}", stateTenantId);
            return Collections.emptyList();
        }

        try {
            Map<String, Object> responseMap = (Map<String, Object>) response.get();
            Map<String, Object> mdmsRes = (Map<String, Object>) responseMap.get("MdmsRes");
            Map<String, Object> module = (Map<String, Object>) mdmsRes.get(mdmsModuleName);
            List<Object> masterData = (List<Object>) module.get(mdmsMasterName);

            return masterData.stream()
                    .map(item -> objectMapper.convertValue(item, MobileValidationConfig.class))
                    .toList();
        } catch (Exception e) {
            log.error("Error parsing MDMS mobile validation config", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildMdmsRequest(String tenantId, RequestInfo requestInfo) {
        Map<String, Object> masterDetail = new HashMap<>();
        masterDetail.put("name", mdmsMasterName);

        Map<String, Object> moduleDetail = new HashMap<>();
        moduleDetail.put("moduleName", mdmsModuleName);
        moduleDetail.put("masterDetails", List.of(masterDetail));

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("tenantId", tenantId);
        criteria.put("moduleDetails", List.of(moduleDetail));

        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", requestInfo);
        request.put("MdmsCriteria", criteria);
        return request;
    }
}
