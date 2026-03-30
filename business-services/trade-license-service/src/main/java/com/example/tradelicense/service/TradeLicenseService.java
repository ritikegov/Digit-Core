package com.example.tradelicense.service;

import com.example.tradelicense.web.models.*;

import java.util.List;

public interface TradeLicenseService {

    TradeLicenseResponse create(TradeLicenseRequest request);

    TradeLicenseResponse update(TradeLicenseRequest request);

    TradeLicenseResponse search(TradeLicenseSearchCriteria criteria);

}
