package com.kkpp.auth.domain;

import static com.kkpp.auth.testsupport.AuthTestEntityFactory.PHONE;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.user;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.userAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthDomainTest {

    @Test
    void userCreateInitializesPublicIdAndActiveStatus() {
        User created = User.create(
                "홍길동",
                PHONE,
                "v2$resident-hash",
                "v2$resident-enc",
                "서울시 강남구",
                "101호",
                "12345"
        );

        assertThat(created.getPublicId()).isNotNull();
        assertThat(created.getName()).isEqualTo("홍길동");
        assertThat(created.getPhone()).isEqualTo(PHONE);
        assertThat(created.getResidentIdHash()).isEqualTo("v2$resident-hash");
        assertThat(created.getResidentIdEnc()).isEqualTo("v2$resident-enc");
        assertThat(created.getAddress()).isEqualTo("서울시 강남구");
        assertThat(created.getAddressDetail()).isEqualTo("101호");
        assertThat(created.getZipCode()).isEqualTo("12345");
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE.name());
    }

    @Test
    void updateAddressChangesAddressFields() {
        User user = user();

        user.updateAddress("부산시 해운대구", null, "67890");

        assertThat(user.getAddress()).isEqualTo("부산시 해운대구");
        assertThat(user.getAddressDetail()).isNull();
        assertThat(user.getZipCode()).isEqualTo("67890");
    }

    @Test
    void updateAddressRejectsBlankAddressOrZipCode() {
        User user = user();

        assertThatThrownBy(() -> user.updateAddress(" ", "101호", "12345"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.updateAddress("서울시 강남구", "101호", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userAuthUpdatesTokenPinAndLoginState() {
        User user = user();
        UserAuth userAuth = userAuth(user);

        assertThat(userAuth.getUser()).isEqualTo(user);
        assertThat(userAuth.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userAuth.isPinSet()).isFalse();

        userAuth.updateRefreshToken("refresh-hash");
        userAuth.updatePin("pin-hash");
        userAuth.recordLogin();

        assertThat(userAuth.getRefreshToken()).isEqualTo("refresh-hash");
        assertThat(userAuth.getPinHash()).isEqualTo("pin-hash");
        assertThat(userAuth.getPinChangedAt()).isNotNull();
        assertThat(userAuth.getLastLoginAt()).isNotNull();
        assertThat(userAuth.isPinSet()).isTrue();
        assertThat(user.getPublicId()).isEqualTo(USER_PUBLIC_ID);
    }

    @Test
    void userAuthTreatsBlankPinHashAsNotSet() {
        UserAuth userAuth = userAuth(user());

        userAuth.updatePin(" ");

        assertThat(userAuth.isPinSet()).isFalse();
    }

    @Test
    void adminUserCreateAndStatusTransitions() {
        AdminUser adminUser = AdminUser.create(
                "admin@kkpp.com",
                "encoded-password",
                "관리자",
                AdminRole.ADMIN
        );

        assertThat(adminUser.getPublicId()).isNotNull();
        assertThat(adminUser.getEmail()).isEqualTo("admin@kkpp.com");
        assertThat(adminUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(adminUser.getName()).isEqualTo("관리자");
        assertThat(adminUser.getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(adminUser.getStatus()).isEqualTo("ACTIVE");

        adminUser.recordLogin();
        adminUser.suspend();

        assertThat(adminUser.getLastLoginAt()).isNotNull();
        assertThat(adminUser.getStatus()).isEqualTo("SUSPENDED");

        adminUser.activate();

        assertThat(adminUser.getStatus()).isEqualTo("ACTIVE");
    }
}
