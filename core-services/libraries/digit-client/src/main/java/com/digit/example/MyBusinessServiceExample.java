package com.digit.example;

import com.digit.services.boundary.BoundaryClient;
import com.digit.services.boundary.model.Boundary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyBusinessServiceExample {

    @Autowired
    private BoundaryClient boundaryClient;

    public void doSomething() {
        List<String> codes = List.of("BOUNDARY001", "BOUNDARY002");
        List<Boundary> boundaries = boundaryClient.searchBoundariesByCodes(codes);
    }
}
