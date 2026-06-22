package com.kkpp.auth.testsupport;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import java.lang.reflect.Field;
import java.util.UUID;

public final class AuthTestEntityFactory {

    public static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final Long USER_ID = 10L;
    public static final String PHONE = "01012345678";

    private AuthTestEntityFactory() {
    }

    public static User user() {
        User user = User.create(
                "홍길동",
                PHONE,
                "v2$resident-hash",
                "v2$resident-enc",
                "서울시 강남구",
                "101호",
                "12345"
        );
        set(user, "id", USER_ID);
        set(user, "publicId", USER_PUBLIC_ID);
        return user;
    }

    public static UserAuth userAuth(User user) {
        UserAuth userAuth = UserAuth.create(user, "encoded-password");
        set(userAuth, "id", 20L);
        return userAuth;
    }

    public static void set(Object target, String fieldName, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            throw new IllegalArgumentException("Field not found: " + fieldName);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
