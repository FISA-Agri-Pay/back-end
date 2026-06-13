package com.kkpp.admin.bnpl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BnplDomainTest {

    @Test
    void notificationCreateTrimsValuesAndDefaultsUnread() {
        UUID userPublicId = UUID.randomUUID();

        BnplNotification notification = BnplNotification.create(
                userPublicId,
                " 연체 안내 ",
                "내용",
                " OVERDUE_ALERT_SMS "
        );

        assertThat(notification.getPublicId()).isNotNull();
        assertThat(notification.getUserPublicId()).isEqualTo(userPublicId);
        assertThat(notification.getTitle()).isEqualTo("연체 안내");
        assertThat(notification.getNotificationType()).isEqualTo("OVERDUE_ALERT_SMS");
        assertThat(notification.getRead()).isFalse();
    }

    @Test
    void notificationCreateValidatesRequiredValues() {
        assertThatThrownBy(() -> BnplNotification.create(null, "제목", "내용", "TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BnplNotification.create(UUID.randomUUID(), null, "내용", "TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BnplNotification.create(UUID.randomUUID(), "제목", "내용", "A".repeat(31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditLogCreateCopiesAuditFields() {
        UUID adminPublicId = UUID.randomUUID();
        UUID userPublicId = UUID.randomUUID();
        UUID targetPublicId = UUID.randomUUID();

        BnplAuditLog log = BnplAuditLog.create(
                adminPublicId,
                userPublicId,
                "OVERDUE_ALERT_SENT",
                "notifications",
                targetPublicId,
                "127.0.0.1"
        );

        assertThat(log.getPublicId()).isNotNull();
        assertThat(log.getAdminUserPublicId()).isEqualTo(adminPublicId);
        assertThat(log.getUserPublicId()).isEqualTo(userPublicId);
        assertThat(log.getAction()).isEqualTo("OVERDUE_ALERT_SENT");
        assertThat(log.getTargetTable()).isEqualTo("notifications");
        assertThat(log.getTargetPublicId()).isEqualTo(targetPublicId);
        assertThat(log.getIpAddress()).isEqualTo("127.0.0.1");
    }
}
