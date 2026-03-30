package com.example.tradelicense.client;

import org.digit.services.idgen.IdGenClient;
import com.example.tradelicense.web.models.TradeLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper class for IdGen-related operations.
 * Encapsulates ID generation logic for Trade License.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeLicenseIdGenClient {

    private final IdGenClient idGenClient;

    @Value("${tl.idgen.applicationNumber.templateCode}")
    private String applicationNumberTemplateCode;

    @Value("${tl.idgen.licenseNumber.templateCode}")
    private String licenseNumberTemplateCode;

    /**
     * Generate application number for a trade license.
     *
     * @param tenantId the tenant ID
     * @return generated application number
     */
    public String generateApplicationNumber(String tenantId) {
        log.info("Generating application number for tenant {}", tenantId);
        
        // IdGenClient.generateId expects (templateCode, variables)
        // Variables must include tenantId for the IdGen service
        Map<String, String> variables = new HashMap<>();
        variables.put("tenantId", tenantId);
        
        String applicationNumber = idGenClient.generateId(applicationNumberTemplateCode, variables);
        log.info("Generated application number: {}", applicationNumber);
        
        return applicationNumber;
    }

    /**
     * Generate license number for an approved trade license.
     *
     * @param license the trade license
     * @return generated license number
     */
    public String generateLicenseNumber(TradeLicense license) {
        log.info("Generating license number for application {}", license.getApplicationNumber());
        
        // IdGenClient.generateId expects (templateCode, variables)
        // Variables must include tenantId for the IdGen service
        Map<String, String> variables = new HashMap<>();
        variables.put("tenantId", license.getTenantId());
        
        String licenseNumber = idGenClient.generateId(licenseNumberTemplateCode, variables);
        log.info("Generated license number: {}", licenseNumber);
        
        return licenseNumber;
    }
}
