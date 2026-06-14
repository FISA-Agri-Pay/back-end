package com.kkpp.catalog.testsupport;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.category.domain.Category;
import com.kkpp.catalog.checkout.domain.BnplPaymentRequest;
import com.kkpp.catalog.paymentpin.domain.PaymentPinVerification;
import com.kkpp.catalog.paymentpin.event.PaymentPinVerifiedEvent;
import com.kkpp.catalog.product.domain.Product;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class CatalogTestEntityFactory {

    public static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID PRODUCT_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final UUID PAYMENT_REQUEST_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final UUID VERIFICATION_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    public static final UUID EVENT_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private CatalogTestEntityFactory() {
    }

    public static Category category(Long id, UUID publicId, String name, String status) {
        Category category = instantiate(Category.class);
        set(category, "id", id);
        set(category, "publicId", publicId);
        set(category, "name", name);
        set(category, "status", status);
        return category;
    }

    public static Product product(Long id, UUID publicId, Category category, String status, int stockQuantity, BigDecimal price) {
        Product product = instantiate(Product.class);
        set(product, "id", id);
        set(product, "publicId", publicId);
        set(product, "category", category);
        set(product, "name", "유기질 비료");
        set(product, "description", "토양 개선용 비료");
        set(product, "price", price);
        set(product, "stockQuantity", stockQuantity);
        set(product, "unit", "포");
        set(product, "imageUrl", "fertilizer.png");
        set(product, "status", status);
        return product;
    }

    public static CartItem cartItem(Long id, UUID userPublicId, Product product, int quantity) {
        CartItem cartItem = CartItem.create(userPublicId, product, quantity);
        set(cartItem, "id", id);
        return cartItem;
    }

    public static BnplPaymentRequest paymentRequest(UUID publicId, UUID userPublicId, BigDecimal totalAmount, CartItem cartItem) {
        return BnplPaymentRequest.create(publicId, userPublicId, totalAmount, List.of(cartItem));
    }

    public static PaymentPinVerifiedEvent paymentPinVerifiedEvent(UUID eventId, UUID verificationId, UUID userPublicId) {
        Instant verifiedAt = Instant.parse("2099-06-14T00:00:00Z");
        return new PaymentPinVerifiedEvent(
                eventId,
                verificationId,
                userPublicId,
                verifiedAt,
                verifiedAt.plusSeconds(300),
                PaymentPinVerification.TYPE_PAYMENT_PIN
        );
    }

    public static PaymentPinVerification paymentPinVerification(UUID verificationId, UUID userPublicId) {
        return PaymentPinVerification.from(paymentPinVerifiedEvent(EVENT_ID, verificationId, userPublicId));
    }

    public static PaymentPinVerification expiredPaymentPinVerification(UUID verificationId, UUID userPublicId) {
        PaymentPinVerification verification = PaymentPinVerification.from(paymentPinVerifiedEvent(EVENT_ID, verificationId, userPublicId));
        set(verification, "expiresAt", LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        return verification;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("테스트 엔티티 생성 실패: " + type.getSimpleName(), exception);
        }
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
            throw new NoSuchFieldException(fieldName);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("테스트 필드 설정 실패: " + fieldName, exception);
        }
    }
}
