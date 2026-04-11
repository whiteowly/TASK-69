package com.croh.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrganizationCredentialDocumentRepository extends JpaRepository<OrganizationCredentialDocument, Long> {

    List<OrganizationCredentialDocument> findByAccountId(Long accountId);

    List<OrganizationCredentialDocument> findByStatus(String status);

    boolean existsByChecksum(String checksum);
}
