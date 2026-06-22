package com.kkpp.admin.order.domain;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.adminOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kkpp.common.core.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminOrderDomainTest {

    @Test
    void changeDeliveryStatusUpdatesActiveOrder() {
        AdminOrder order = adminOrder(UUID.randomUUID(), OrderStatus.CONFIRMED, DeliveryStatus.PREPARING);

        order.changeDeliveryStatus(DeliveryStatus.SHIPPING);

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    void changeDeliveryStatusRejectsNullAndCancelledOrder() {
        AdminOrder order = adminOrder(UUID.randomUUID(), OrderStatus.CONFIRMED, DeliveryStatus.PREPARING);
        AdminOrder cancelled = adminOrder(UUID.randomUUID(), OrderStatus.CANCELLED, DeliveryStatus.CANCELLED);

        assertThatThrownBy(() -> order.changeDeliveryStatus(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cancelled.changeDeliveryStatus(DeliveryStatus.SHIPPING))
                .isInstanceOf(BusinessException.class);
    }
}
