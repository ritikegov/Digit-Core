package com.example.tradelicense.service;

import com.example.tradelicense.web.models.TradeLicense;
import org.digit.services.registry.model.RegistryDataResponse;

import java.util.List;

public interface TradeLicenseRegistryService {

    TradeLicense findByApplicationNumber(String applicationNumber, String tenantId);

    List<TradeLicense> findByLicenseNumber(String licenseNumber, String tenantId);

    List<TradeLicense> findByTradeName(String tradeName, String tenantId);

    List<TradeLicense> findByMobileNumber(String mobileNumber, String tenantId);

    List<TradeLicense> findByTenantId(String tenantId);

    RegistryDataResponse createRegistryData(TradeLicense tradeLicense);

    RegistryDataResponse updateRegistryData(TradeLicense tradeLicense);
}
