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

@Entity
@Table(name = "farmer_profiles", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String farmAddress;

    @Column(nullable = false)
    private BigDecimal fieldAreaM2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CropType mainCrop;

    @Column(nullable = false)
    private Boolean hasCropInsurance;

    private LocalDate farmingSince;

    public static FarmerProfile create(Long userId, String farmAddress, BigDecimal fieldAreaM2,
                                       CropType mainCrop, Boolean hasCropInsurance) {
        FarmerProfile profile = new FarmerProfile();
        profile.userId = userId;
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
