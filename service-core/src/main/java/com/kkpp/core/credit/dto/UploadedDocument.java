package com.kkpp.core.credit.dto;

import com.kkpp.core.credit.domain.RequiredDocumentType;

public record UploadedDocument(
        RequiredDocumentType documentType,
        String fileUrl
) {}
