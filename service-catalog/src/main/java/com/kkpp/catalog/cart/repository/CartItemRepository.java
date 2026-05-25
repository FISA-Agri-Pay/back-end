package com.kkpp.catalog.cart.repository;

import com.kkpp.catalog.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product p
            join fetch p.category
            where ci.user.id = :userId
            order by ci.id desc
            """)
    List<CartItem> findAllByUserIdWithProduct(@Param("userId") Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product p
            join fetch p.category
            where ci.user.id = :userId
              and ci.id in :cartItemIds
            order by ci.id
            """)
    List<CartItem> findAllByUserIdAndIdInWithProduct(
            @Param("userId") Long userId,
            @Param("cartItemIds") Collection<Long> cartItemIds
    );
}
