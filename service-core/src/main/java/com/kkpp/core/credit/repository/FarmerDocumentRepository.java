package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.FarmerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmerDocumentRepository extends JpaRepository<FarmerDocument, Long> {
}
