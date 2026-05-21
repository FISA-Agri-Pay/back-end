package com.kkpp.core.credit.dto.request;

public record LandRequest(
        String sessionId,
        String address,
        Integer areaSize
) {}
