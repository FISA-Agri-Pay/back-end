package com.kkpp.admin.adminauth.domain;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.adminAuthUser;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAuthUserDomainTest {

    @Test
    void adminAuthUserUpdatesLoginAndRefreshTokenState() {
        AdminAuthUser user = adminAuthUser(UUID.randomUUID(), "ADMIN", "ACTIVE");

        user.recordLogin();
        user.updateRefreshToken("refresh-hash");

        assertThat(user.isActive()).isTrue();
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getRefreshToken()).isEqualTo("refresh-hash");

        user.clearRefreshToken();

        assertThat(user.getRefreshToken()).isNull();
    }
}
