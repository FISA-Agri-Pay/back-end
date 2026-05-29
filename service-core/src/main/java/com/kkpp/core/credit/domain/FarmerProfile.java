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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "farmer_profiles", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false, unique = true)
    private UUID userPublicId;

    @Column(nullable = false)
    private String farmAddress;

    @Column
    private String farmAddressDetail;

    @Column(nullable = false, length = 20)
    private String farmZipCode;

    @Column(name = "field_area_m2", nullable = false)
    private BigDecimal fieldAreaM2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CropType mainCrop;

    @Column(nullable = false)
    private Boolean hasCropInsurance;

    @Column(nullable = false)
    private Integer farmingSince;

    public static FarmerProfile create(UUID userPublicId, String farmAddress, BigDecimal fieldAreaM2,
                                       CropType mainCrop, Boolean hasCropInsurance) {
        FarmerProfile profile = new FarmerProfile();
        profile.publicId = UUID.randomUUID();
        profile.userPublicId = userPublicId;
        profile.farmZipCode = "";
        profile.farmingSince = 1;
        profile.update(farmAddress, fieldAreaM2, mainCrop, hasCropInsurance);
        return profile;
    }

    public void update(String farmAddress, BigDecimal fieldAreaM2, CropType mainCrop, Boolean hasCropInsurance) {
        this.farmAddress = farmAddress;
        this.fieldAreaM2 = fieldAreaM2;
        this.mainCrop = mainCrop;
        this.hasCropInsurance = hasCropInsurance;
    }
}
