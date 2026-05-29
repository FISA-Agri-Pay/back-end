package com.kkpp.core.credit.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
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

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "application_public_id")
    private UUID applicationPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequiredDocumentType documentType;

    @Column(nullable = false)
    private String fileUrl;

    public static FarmerDocument create(UUID userPublicId, UUID applicationPublicId,
                                        RequiredDocumentType documentType, String fileUrl,
                                        CreditLimitApplication application) {
        FarmerDocument document = new FarmerDocument();
        document.publicId = UUID.randomUUID();
        document.userPublicId = userPublicId;
        document.applicationPublicId = applicationPublicId;
        document.documentType = documentType;
        document.fileUrl = fileUrl;
        return document;
    }
}
