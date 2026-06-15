package com.kkpp.auth.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 30)
    private String phone;

    @Column(nullable = false, length = 255)
    private String residentIdHash;

    @Column(length = 500)
    private String residentIdEnc;

    @Column(nullable = false)
    private String address;

    @Column
    private String addressDetail;

    @Column(nullable = false, length = 20)
    private String zipCode;

    @Column(nullable = false, length = 20)
    private String status;

    public static User create(
            String name,
            String phone,
            String residentIdHash,
            String residentIdEnc,
            String address,
            String addressDetail,
            String zipCode
    ) {
        User user = new User();
        user.publicId = UUID.randomUUID();
        user.name = name;
        user.phone = phone;
        user.residentIdHash = residentIdHash;
        user.residentIdEnc = residentIdEnc;
        user.address = address;
        user.addressDetail = addressDetail;
        user.zipCode = zipCode;
        user.status = UserStatus.ACTIVE.name();
        return user;
    }

    public void updateAddress(String address, String addressDetail, String zipCode) {
        this.address = address;
        this.addressDetail = addressDetail;
        this.zipCode = zipCode;
    }

    public void withdraw() {
        this.status = UserStatus.INACTIVE.name();
    }

    public boolean isActive() {
        return UserStatus.ACTIVE.name().equals(this.status);
    }
}

