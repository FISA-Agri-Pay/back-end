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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "farmer_profiles", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_public_id", nullable = false, unique = true)
    private UUID userPublicId;

    @Column(name = "farm_address", nullable = false)
    private String farmAddress;

    @Column(name = "farm_address_detail")
    private String farmAddressDetail;

    @Column(name = "farm_zip_code", nullable = false, length = 10)
    private String farmZipCode;

    @Column(name = "field_area_m2", nullable = false)
    private BigDecimal fieldAreaM2;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_crop", nullable = false)
    private CropType mainCrop;

    @Column(name = "has_crop_insurance", nullable = false)
    private Boolean hasCropInsurance;

    @Column(name = "farming_since", nullable = false)
    private Integer farmingSince;

    public static FarmerProfile create(UUID userPublicId, String farmAddress, BigDecimal fieldAreaM2,
                                       CropType mainCrop, Boolean hasCropInsurance) {
        FarmerProfile profile = new FarmerProfile();
        profile.userPublicId = userPublicId;
        profile.farmAddressDetail = "";
        profile.farmZipCode = "00000";
        profile.farmingSince = LocalDate.now().getYear();
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
