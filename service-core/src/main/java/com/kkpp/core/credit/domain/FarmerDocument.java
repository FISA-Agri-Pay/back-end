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

import java.util.UUID;

@Entity
@Table(name = "farmer_documents", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private RequiredDocumentType documentType;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_public_id", referencedColumnName = "public_id")
    private CreditLimitApplication application;

    public static FarmerDocument create(RequiredDocumentType documentType, String fileUrl,
                                        CreditLimitApplication application) {
        FarmerDocument document = new FarmerDocument();
        document.userPublicId = application.getUserPublicId();
        document.documentType = documentType;
        document.fileUrl = fileUrl;
        document.application = application;
        return document;
    }
}
