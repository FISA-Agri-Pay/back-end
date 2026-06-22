package com.kkpp.catalog.cart.repository;

import com.kkpp.catalog.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product p
            join fetch p.category
            where ci.userPublicId = :userPublicId
            order by ci.id desc
            """)
    List<CartItem> findAllByUserPublicIdWithProduct(@Param("userPublicId") UUID userPublicId);

    Optional<CartItem> findByUserPublicIdAndProductPublicId(UUID userPublicId, UUID productPublicId);

    Optional<CartItem> findByIdAndUserPublicId(Long id, UUID userPublicId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product p
            join fetch p.category
            where ci.userPublicId = :userPublicId
              and ci.id in :cartItemIds
            order by ci.id
            """)
    List<CartItem> findAllByUserPublicIdAndIdInWithProduct(
            @Param("userPublicId") UUID userPublicId,
            @Param("cartItemIds") Collection<Long> cartItemIds
    );
}
