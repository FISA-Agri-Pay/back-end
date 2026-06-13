package com.kkpp.admin.order.service;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.adminOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.admin.order.domain.AdminOrder;
import com.kkpp.admin.order.domain.DeliveryStatus;
import com.kkpp.admin.order.domain.OrderStatus;
import com.kkpp.admin.order.dto.AdminOrderPageResponse;
import com.kkpp.admin.order.dto.AdminOrderSummaryResponse;
import com.kkpp.admin.order.dto.UpdateOrderDeliveryStatusRequest;
import com.kkpp.admin.order.repository.AdminOrderRepository;
import com.kkpp.common.core.exception.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    private static final UUID ORDER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private AdminOrderRepository adminOrderRepository;

    private AdminOrderService adminOrderService;

    @BeforeEach
    void setUp() {
        adminOrderService = new AdminOrderService(adminOrderRepository);
    }

    @Test
    void getOrdersReturnsPageWithNormalizedPageAndSize() {
        AdminOrder order = adminOrder(ORDER_PUBLIC_ID, OrderStatus.CONFIRMED, DeliveryStatus.PREPARING);
        when(adminOrderRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminOrder>>any(),
                any(Pageable.class)
        ))
                .thenReturn(new PageImpl<>(List.of(order)));

        AdminOrderPageResponse response = adminOrderService.getOrders(
                null,
                null,
                null,
                null,
                " 홍길동 ",
                0,
                0
        );

        assertThat(response.orders()).hasSize(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void getOrdersRejectsInvalidDateRange() {
        assertThatThrownBy(() -> adminOrderService.getOrders(
                null,
                null,
                LocalDate.of(2026, 6, 14),
                LocalDate.of(2026, 6, 13),
                null,
                1,
                20
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void getOrdersRethrowsRepositoryRuntimeException() {
        when(adminOrderRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminOrder>>any(),
                any(Pageable.class)
        )).thenThrow(new RuntimeException("repository failed"));

        assertThatThrownBy(() -> adminOrderService.getOrders(
                null,
                null,
                null,
                null,
                null,
                1,
                20
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateDeliveryStatusChangesOrderStatus() {
        AdminOrder order = adminOrder(ORDER_PUBLIC_ID, OrderStatus.CONFIRMED, DeliveryStatus.PREPARING);
        when(adminOrderRepository.findByPublicId(ORDER_PUBLIC_ID)).thenReturn(Optional.of(order));

        AdminOrderSummaryResponse response = adminOrderService.updateDeliveryStatus(
                ORDER_PUBLIC_ID,
                new UpdateOrderDeliveryStatusRequest(DeliveryStatus.SHIPPING)
        );

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    void updateDeliveryStatusThrowsWhenOrderDoesNotExist() {
        when(adminOrderRepository.findByPublicId(ORDER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderService.updateDeliveryStatus(
                ORDER_PUBLIC_ID,
                new UpdateOrderDeliveryStatusRequest(DeliveryStatus.SHIPPING)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void updateDeliveryStatusRethrowsRepositoryRuntimeException() {
        when(adminOrderRepository.findByPublicId(ORDER_PUBLIC_ID))
                .thenThrow(new RuntimeException("repository failed"));

        assertThatThrownBy(() -> adminOrderService.updateDeliveryStatus(
                ORDER_PUBLIC_ID,
                new UpdateOrderDeliveryStatusRequest(DeliveryStatus.SHIPPING)
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void orderSearchSpecificationsBuildPredicatesForProvidedFilters() {
        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("orderStatus")).thenReturn(path);
        when(root.get("deliveryStatus")).thenReturn(path);
        when(root.get("orderedAt")).thenReturn(path);
        when(criteriaBuilder.equal(path, OrderStatus.CONFIRMED)).thenReturn(predicate);
        when(criteriaBuilder.equal(path, DeliveryStatus.SHIPPING)).thenReturn(predicate);
        when(criteriaBuilder.greaterThanOrEqualTo(path, LocalDateTime.of(2026, 6, 1, 0, 0))).thenReturn(predicate);
        when(criteriaBuilder.lessThan(path, LocalDateTime.of(2026, 6, 14, 0, 0))).thenReturn(predicate);

        Specification<AdminOrder> orderStatusSpec = ReflectionTestUtils.invokeMethod(
                adminOrderService,
                "orderStatusEquals",
                OrderStatus.CONFIRMED
        );
        Specification<AdminOrder> deliveryStatusSpec = ReflectionTestUtils.invokeMethod(
                adminOrderService,
                "deliveryStatusEquals",
                DeliveryStatus.SHIPPING
        );
        Specification<AdminOrder> startSpec = ReflectionTestUtils.invokeMethod(
                adminOrderService,
                "orderedAtGreaterThanOrEqualTo",
                LocalDateTime.of(2026, 6, 1, 0, 0)
        );
        Specification<AdminOrder> endSpec = ReflectionTestUtils.invokeMethod(
                adminOrderService,
                "orderedAtLessThan",
                LocalDateTime.of(2026, 6, 14, 0, 0)
        );

        assertThat(orderStatusSpec.toPredicate(root, query, criteriaBuilder)).isEqualTo(predicate);
        assertThat(deliveryStatusSpec.toPredicate(root, query, criteriaBuilder)).isEqualTo(predicate);
        assertThat(startSpec.toPredicate(root, query, criteriaBuilder)).isEqualTo(predicate);
        assertThat(endSpec.toPredicate(root, query, criteriaBuilder)).isEqualTo(predicate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void orderSearchSpecificationsReturnNullWhenFiltersAreMissing() {
        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);

        Specification<AdminOrder> orderStatusSpec = ReflectionTestUtils.invokeMethod(adminOrderService, "orderStatusEquals", (Object) null);
        Specification<AdminOrder> deliveryStatusSpec = ReflectionTestUtils.invokeMethod(adminOrderService, "deliveryStatusEquals", (Object) null);
        Specification<AdminOrder> startSpec = ReflectionTestUtils.invokeMethod(adminOrderService, "orderedAtGreaterThanOrEqualTo", (Object) null);
        Specification<AdminOrder> endSpec = ReflectionTestUtils.invokeMethod(adminOrderService, "orderedAtLessThan", (Object) null);
        Specification<AdminOrder> keywordSpec = ReflectionTestUtils.invokeMethod(adminOrderService, "keywordContains", " ");

        assertThat(orderStatusSpec.toPredicate(root, query, criteriaBuilder)).isNull();
        assertThat(deliveryStatusSpec.toPredicate(root, query, criteriaBuilder)).isNull();
        assertThat(startSpec.toPredicate(root, query, criteriaBuilder)).isNull();
        assertThat(endSpec.toPredicate(root, query, criteriaBuilder)).isNull();
        assertThat(keywordSpec.toPredicate(root, query, criteriaBuilder)).isNull();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keywordSpecificationSearchesUserAndRecipientFields() {
        Root root = mock(Root.class);
        Join user = mock(Join.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path namePath = mock(Path.class);
        Path phonePath = mock(Path.class);
        Path recipientNamePath = mock(Path.class);
        Path recipientPhonePath = mock(Path.class);
        Expression loweredUserName = mock(Expression.class);
        Expression loweredRecipientName = mock(Expression.class);
        Predicate userNamePredicate = mock(Predicate.class);
        Predicate userPhonePredicate = mock(Predicate.class);
        Predicate recipientNamePredicate = mock(Predicate.class);
        Predicate recipientPhonePredicate = mock(Predicate.class);
        Predicate combinedPredicate = mock(Predicate.class);

        when(root.join("user", JoinType.INNER)).thenReturn(user);
        when(user.get("name")).thenReturn(namePath);
        when(user.get("phone")).thenReturn(phonePath);
        when(root.get("recipientName")).thenReturn(recipientNamePath);
        when(root.get("recipientPhone")).thenReturn(recipientPhonePath);
        when(criteriaBuilder.lower(namePath)).thenReturn(loweredUserName);
        when(criteriaBuilder.lower(recipientNamePath)).thenReturn(loweredRecipientName);
        when(criteriaBuilder.like(loweredUserName, "%hong%")).thenReturn(userNamePredicate);
        when(criteriaBuilder.like(phonePath, "%hong%")).thenReturn(userPhonePredicate);
        when(criteriaBuilder.like(loweredRecipientName, "%hong%")).thenReturn(recipientNamePredicate);
        when(criteriaBuilder.like(recipientPhonePath, "%hong%")).thenReturn(recipientPhonePredicate);
        when(criteriaBuilder.or(userNamePredicate, userPhonePredicate, recipientNamePredicate, recipientPhonePredicate))
                .thenReturn(combinedPredicate);

        Specification<AdminOrder> keywordSpec = ReflectionTestUtils.invokeMethod(adminOrderService, "keywordContains", " Hong ");

        assertThat(keywordSpec.toPredicate(root, query, criteriaBuilder)).isEqualTo(combinedPredicate);
        verify(root).join("user", JoinType.INNER);
    }
}
