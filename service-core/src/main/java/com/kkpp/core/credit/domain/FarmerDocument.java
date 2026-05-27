package com.kkpp.core.credit.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "farmer_documents", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequiredDocumentType documentType;

    @Column(nullable = false)
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CreditLimitApplication application;

    public static FarmerDocument create(RequiredDocumentType documentType, String fileUrl,
                                        CreditLimitApplication application) {
        FarmerDocument document = new FarmerDocument();
        document.documentType = documentType;
        document.fileUrl = fileUrl;
        document.application = application;
        return document;
    }
}
