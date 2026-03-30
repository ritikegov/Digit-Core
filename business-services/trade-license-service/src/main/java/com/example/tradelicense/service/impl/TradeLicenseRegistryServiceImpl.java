package com.example.tradelicense.service.impl;

import com.example.tradelicense.service.TradeLicenseRegistryService;
import com.example.tradelicense.web.models.TradeLicense;
import com.example.tradelicense.client.TradeLicenseRegistryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.registry.model.RegistryDataResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeLicenseRegistryServiceImpl implements TradeLicenseRegistryService {

    private final TradeLicenseRegistryClient registryClient;

    @Override
    public RegistryDataResponse createRegistryData(TradeLicense tradeLicense) {
        return registryClient.createTradeLicense(tradeLicense);
    }

    @Override
    public TradeLicense findByApplicationNumber(String applicationNumber, String tenantId) {
        return registryClient.findByApplicationNumber(applicationNumber, tenantId);
    }

    @Override
    public List<TradeLicense> findByLicenseNumber(String licenseNumber, String tenantId) {
        return registryClient.findByLicenseNumber(licenseNumber, tenantId);
    }

    @Override
    public List<TradeLicense> findByTradeName(String tradeName, String tenantId) {
        return registryClient.findByTradeName(tradeName, tenantId);
    }

    @Override
    public List<TradeLicense> findByMobileNumber(String mobileNumber, String tenantId) {
        return registryClient.findByMobileNumber(mobileNumber, tenantId);
    }

    @Override
    public List<TradeLicense> findByTenantId(String tenantId) {
        return registryClient.findByTenantId(tenantId);
    }

    @Override
    public RegistryDataResponse updateRegistryData(TradeLicense tradeLicense) {
        return registryClient.updateTradeLicense(tradeLicense);
    }
}
