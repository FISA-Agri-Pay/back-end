package com.kkpp.core.credit.dto.response;

public record SubmitResponse(
        String applicationId,
        String status,
        String estimatedCompletion
) {}
