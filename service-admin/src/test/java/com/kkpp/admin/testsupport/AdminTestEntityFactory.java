package com.kkpp.admin.testsupport;

import com.kkpp.admin.adminauth.domain.AdminAuthUser;
import com.kkpp.admin.bnpl.domain.BnplAdminUser;
import com.kkpp.admin.bnpl.domain.BnplCreditLimit;
import com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus;
import com.kkpp.admin.bnpl.domain.BnplUser;
import com.kkpp.admin.credit.domain.CreditReviewApplication;
import com.kkpp.admin.credit.domain.CreditReviewAssScore;
import com.kkpp.admin.credit.domain.CreditReviewDocument;
import com.kkpp.admin.credit.domain.CreditReviewDocumentType;
import com.kkpp.admin.credit.domain.CreditReviewFarmerProfile;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.domain.CreditReviewUser;
import com.kkpp.admin.order.domain.AdminOrder;
import com.kkpp.admin.order.domain.AdminOrderUser;
import com.kkpp.admin.order.domain.DeliveryStatus;
import com.kkpp.admin.order.domain.OrderStatus;
import com.kkpp.admin.product.domain.Category;
import com.kkpp.admin.product.domain.CategoryStatus;
import com.kkpp.admin.product.domain.Product;
import com.kkpp.admin.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class AdminTestEntityFactory {

    private AdminTestEntityFactory() {
    }

    public static Category category(Long id, String name, CategoryStatus status) {
        Category category = Category.create(name, status);
        set(category, "id", id);
        return category;
    }

    public static Product product(Long id, Category category, ProductStatus status) {
        Product product = Product.create(
                category,
                "비료",
                "친환경 비료",
                new BigDecimal("12000"),
                10,
                "포",
                "https://images/product.png",
                status
        );
        set(product, "id", id);
        return product;
    }

    public static AdminAuthUser adminAuthUser(UUID publicId, String role, String status) {
        AdminAuthUser user = instantiate(AdminAuthUser.class);
        set(user, "id", 1L);
        set(user, "publicId", publicId);
        set(user, "email", "admin@kkpp.com");
        set(user, "passwordHash", "encoded-password");
        set(user, "name", "관리자");
        set(user, "role", role);
        set(user, "status", status);
        return user;
    }

    public static BnplAdminUser bnplAdminUser(UUID publicId, String status) {
        BnplAdminUser user = instantiate(BnplAdminUser.class);
        set(user, "id", 1L);
        set(user, "publicId", publicId);
        set(user, "email", "admin@kkpp.com");
        set(user, "passwordHash", "encoded-password");
        set(user, "name", "관리자");
        set(user, "role", "ADMIN");
        set(user, "status", status);
        return user;
    }

    public static BnplUser bnplUser(UUID publicId, String phone) {
        BnplUser user = instantiate(BnplUser.class);
        set(user, "id", 2L);
        set(user, "publicId", publicId);
        set(user, "name", "홍길동");
        set(user, "phone", phone);
        set(user, "status", "ACTIVE");
        return user;
    }

    public static BnplCreditLimit bnplCreditLimit(UUID userPublicId) {
        BnplCreditLimit limit = instantiate(BnplCreditLimit.class);
        set(limit, "id", 3L);
        set(limit, "publicId", UUID.randomUUID());
        set(limit, "userPublicId", userPublicId);
        set(limit, "applicationPublicId", UUID.randomUUID());
        set(limit, "totalLimit", new BigDecimal("3000000"));
        set(limit, "usedAmount", new BigDecimal("1000000"));
        set(limit, "interestRate", new BigDecimal("0.0450"));
        set(limit, "interestDueDay", 11);
        set(limit, "principalDueDate", LocalDate.now().plusMonths(6));
        set(limit, "status", BnplCreditLimitStatus.ACTIVE);
        return limit;
    }

    public static AdminOrder adminOrder(UUID publicId, OrderStatus orderStatus, DeliveryStatus deliveryStatus) {
        AdminOrderUser user = instantiate(AdminOrderUser.class);
        UUID userPublicId = UUID.randomUUID();
        set(user, "id", 20L);
        set(user, "publicId", userPublicId);
        set(user, "name", "홍길동");
        set(user, "phone", "010-0000-0000");

        AdminOrder order = instantiate(AdminOrder.class);
        set(order, "id", 10L);
        set(order, "publicId", publicId);
        set(order, "userPublicId", userPublicId);
        set(order, "user", user);
        set(order, "paymentRequestPublicId", UUID.randomUUID());
        set(order, "totalAmount", new BigDecimal("70000"));
        set(order, "orderStatus", orderStatus);
        set(order, "deliveryStatus", deliveryStatus);
        set(order, "recipientName", "수령인");
        set(order, "recipientPhone", "010-1111-2222");
        set(order, "deliveryAddress", "서울시");
        set(order, "deliveryZipCode", "00000");
        set(order, "orderedAt", LocalDateTime.of(2026, 6, 13, 10, 0));
        return order;
    }

    public static CreditReviewApplication creditReviewApplication(
            Long id,
            UUID publicId,
            UUID userPublicId,
            CreditReviewStatus status
    ) {
        CreditReviewUser user = instantiate(CreditReviewUser.class);
        set(user, "id", 20L);
        set(user, "publicId", userPublicId);
        set(user, "name", "홍길동");
        set(user, "phone", "010-0000-0000");
        set(user, "residentIdHash", "hash");
        set(user, "address", "경기도 안성시");
        set(user, "addressDetail", "101동");
        set(user, "zipCode", "17500");
        set(user, "status", "ACTIVE");

        CreditReviewApplication application = instantiate(CreditReviewApplication.class);
        set(application, "id", id);
        set(application, "publicId", publicId);
        set(application, "user", user);
        set(application, "requestedAmount", new BigDecimal("1000000"));
        set(application, "reapplication", false);
        set(application, "status", status);
        set(application, "appliedAt", LocalDateTime.of(2026, 6, 13, 10, 0));
        return application;
    }

    public static CreditReviewFarmerProfile farmerProfile(String mainCrop) {
        CreditReviewFarmerProfile profile = instantiate(CreditReviewFarmerProfile.class);
        set(profile, "id", 30L);
        set(profile, "mainCrop", mainCrop);
        set(profile, "farmAddress", "경기도 안성시");
        set(profile, "farmZipCode", "17500");
        set(profile, "fieldAreaM2", new BigDecimal("991.735500"));
        set(profile, "hasCropInsurance", true);
        set(profile, "farmingSince", 3);
        return profile;
    }

    public static CreditReviewAssScore assScore(CreditReviewApplication application) {
        CreditReviewAssScore score = instantiate(CreditReviewAssScore.class);
        set(score, "id", 40L);
        set(score, "application", application);
        set(score, "user", application.getUser());
        set(score, "estimatedIncome", new BigDecimal("15000000"));
        set(score, "priceSnapshotDate", LocalDate.of(2026, 6, 13));
        set(score, "incomeScore", 36);
        set(score, "insuranceScore", 25);
        set(score, "farmingCareerScore", 7);
        set(score, "totalScore", 68);
        set(score, "calculatedAt", LocalDateTime.of(2026, 6, 13, 10, 0));
        return score;
    }

    public static CreditReviewDocument reviewDocument(CreditReviewApplication application) {
        CreditReviewDocument document = instantiate(CreditReviewDocument.class);
        set(document, "id", 50L);
        set(document, "application", application);
        set(document, "user", application.getUser());
        set(document, "documentType", CreditReviewDocumentType.AGRI_MANAGEMENT_REGISTRATION);
        set(document, "fileUrl", "documents/agri.pdf");
        set(document, "createdAt", LocalDateTime.of(2026, 6, 13, 10, 0));
        return document;
    }

    public static void set(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("테스트 엔티티 생성 실패: " + type.getName(), exception);
        }
    }
}
