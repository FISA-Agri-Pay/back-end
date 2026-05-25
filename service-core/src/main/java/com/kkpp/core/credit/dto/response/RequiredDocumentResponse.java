package com.kkpp.core.credit.dto.response;

import java.io.Serializable;

public record RequiredDocumentResponse(
        String documentCode,
        String documentName,
        boolean isRequired
) implements Serializable {}
