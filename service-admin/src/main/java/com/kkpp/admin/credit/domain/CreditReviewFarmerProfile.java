package com.kkpp.admin.credit.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = "farmer_profiles", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 신청자가 입력한 농지와 영농 정보를 조회하기 위한 farmer_profiles 테이블 매핑 엔티티
// 관리자 상세 화면의 주소, 경작 면적, 대표 작물, 보험 가입 여부 표시 근거가 된다.
public class CreditReviewFarmerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_public_id", referencedColumnName = "public_id", nullable = false)
    private CreditReviewUser user;

    @Column(name = "farm_address", nullable = false, length = 255)
    private String farmAddress;

    @Column(name = "farm_address_detail", length = 255)
    private String farmAddressDetail;

    @Column(name = "farm_zip_code", nullable = false, length = 10)
    private String farmZipCode;

    @Column(name = "field_area_m2", nullable = false, precision = 12, scale = 2)
    private BigDecimal fieldAreaM2;

    @Column(name = "main_crop", nullable = false, length = 50)
    private String mainCrop;

    @Column(name = "has_crop_insurance", nullable = false)
    private Boolean hasCropInsurance;

    @Column(name = "farming_since", nullable = false)
    private Integer farmingSince;
}
