package com.kkpp.admin.order.repository;

import com.kkpp.admin.order.domain.AdminOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface AdminOrderRepository extends JpaRepository<AdminOrder, Long>, JpaSpecificationExecutor<AdminOrder> {

    @Override
    @EntityGraph(attributePaths = "user")
    Page<AdminOrder> findAll(Specification<AdminOrder> specification, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<AdminOrder> findByPublicId(UUID publicId);
}
