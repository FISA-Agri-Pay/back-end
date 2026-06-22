package com.kkpp.catalog.product.repository;

import com.kkpp.catalog.product.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p
            from Product p
            join fetch p.category c
            where (:categoryId is null or c.id = :categoryId)
              and (:keyword is null or lower(p.name) like lower(concat('%', cast(:keyword as string), '%')))
              and p.status <> 'HIDDEN'
            order by p.id desc
            """)
    List<Product> search(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    @Query("""
            select p
            from Product p
            join fetch p.category
            where p.publicId = :publicId
            """)
    Optional<Product> findByPublicIdWithCategory(@Param("publicId") UUID publicId);

    @Query("""
            select p
            from Product p
            join fetch p.category
            where p.publicId in :publicIds
              and p.status = 'ON_SALE'
              and p.stockQuantity > 0
            """)
    List<Product> findSellableByPublicIds(@Param("publicIds") List<UUID> publicIds);

    @Query("""
            select p
            from Product p
            join fetch p.category
            where p.status = 'ON_SALE'
              and p.stockQuantity > 0
            """)
    List<Product> findAllSellable();
}
