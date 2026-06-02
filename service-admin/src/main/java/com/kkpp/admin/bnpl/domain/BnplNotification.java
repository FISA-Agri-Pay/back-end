package com.kkpp.admin.bnpl.domain;

import com.kkpp.common.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 관리자 알림 발송 이력을 저장하는 notifications 테이블 매핑 엔티티
// 알림 발송 후 해당 사용자의 마지막 발송 시각(alertSentAt) 조회에도 활용된다.
public class BnplNotification extends BaseTimeEntity {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_NOTIFICATION_TYPE_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "notification_type", nullable = false, length = 30)
    private String notificationType;

    @Column(name = "is_read", nullable = false)
    private Boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static BnplNotification create(
            UUID userPublicId,
            String title,
            String content,
            String notificationType
    ) {
        if (userPublicId == null) {
            throw new IllegalArgumentException("userPublicId must not be null.");
        }
        String validatedTitle = validateRequiredLength(title, "title", MAX_TITLE_LENGTH);
        String validatedNotificationType = validateRequiredLength(
                notificationType,
                "notificationType",
                MAX_NOTIFICATION_TYPE_LENGTH
        );

        BnplNotification notification = new BnplNotification();
        notification.publicId = UUID.randomUUID();
        notification.userPublicId = userPublicId;
        notification.title = validatedTitle;
        notification.content = content;
        notification.notificationType = validatedNotificationType;
        notification.read = false;
        return notification;
    }

    private static String validateRequiredLength(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }
        String trimmedValue = value.trim();
        if (trimmedValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be less than or equal to " + maxLength + ".");
        }
        return trimmedValue;
    }
}
