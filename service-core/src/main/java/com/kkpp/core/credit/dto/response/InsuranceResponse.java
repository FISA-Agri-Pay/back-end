package com.kkpp.core.credit.dto.response;

import java.util.List;

public record InsuranceResponse(
        List<RequiredDocumentResponse> requiredDocuments
) {}
