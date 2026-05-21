package com.kkpp.core.credit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.dto.response.RequiredDocumentResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreditApplicationDraft implements Serializable {

    private String sessionId;
    private Long userId;
    private String address;
    private BigDecimal areaSizeM2;
    private CropType cropType;
    private Boolean hasCropInsurance;
    private List<RequiredDocumentResponse> requiredDocuments;

    @JsonIgnore
    public boolean isLandFilled() {
        return address != null && !address.isBlank()
                && areaSizeM2 != null
                && areaSizeM2.compareTo(BigDecimal.ZERO) > 0;
    }
}
