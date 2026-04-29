package org.egov.internalgatewayscg.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

@Slf4j
@Component
public class RoutingConfig {

    @Value("${egov.service.config.path}")
    private String serviceConfigPath;

    @Autowired
    private ResourceLoader resourceLoader;

    private Map<String, Map<String, String>> tenantRoutingConfigWrapper;

    @PostConstruct
    public void loadServiceConfigurationYaml() {

        log.info(" Translator Service Reading Configuration from tenant-config givne in path : " + serviceConfigPath);
        ObjectMapper mapper = new ObjectMapper();
        try {
            Resource resource = resourceLoader.getResource(serviceConfigPath);
            tenantRoutingConfigWrapper = mapper.readValue(new InputStreamReader(resource.getInputStream()),
                    new TypeReference<Map<String, Map<String, String>>>(){});

            log.info("loging the map constructed from the cofig file : " + tenantRoutingConfigWrapper.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Map<String, String>> getTeanantRoutingConfigWrapper() {
        return tenantRoutingConfigWrapper;
    }

}