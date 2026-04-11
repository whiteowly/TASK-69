package com.croh.verification;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditService;
import com.croh.crypto.EncryptionService;
import com.croh.files.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VerificationService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB

    private final PersonVerificationRepository personVerificationRepository;
    private final OrganizationCredentialDocumentRepository orgDocumentRepository;
    private final AccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public VerificationService(PersonVerificationRepository personVerificationRepository,
                               OrganizationCredentialDocumentRepository orgDocumentRepository,
                               AccountRepository accountRepository,
                               EncryptionService encryptionService,
                               FileStorageService fileStorageService,
                               AuditService auditService) {
        this.personVerificationRepository = personVerificationRepository;
        this.orgDocumentRepository = orgDocumentRepository;
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public PersonVerification submitPersonVerification(Long accountId, String legalName, String dateOfBirth) {
        String encryptedDob = encryptionService.encrypt(dateOfBirth);

        PersonVerification pv = new PersonVerification();
        pv.setAccountId(accountId);
        pv.setLegalName(legalName);
        pv.setDobEncrypted(encryptedDob);
        pv.setStatus("UNDER_REVIEW");
        pv.setCreatedAt(LocalDateTime.now());
        pv.setUpdatedAt(LocalDateTime.now());

        PersonVerification saved = personVerificationRepository.save(pv);

        auditService.log(accountId, null, "PERSON_VERIFICATION_SUBMITTED",
                "PersonVerification", saved.getId().toString(),
                null, "UNDER_REVIEW", null, null);

        return saved;
    }

    @Transactional
    public OrganizationCredentialDocument submitOrgCredential(Long accountId, String fileName,
                                                              String contentType, long fileSize,
                                                              byte[] content) {
        // Only ORGANIZATION accounts can submit org credentials
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (account.getAccountType() != Account.AccountType.ORGANIZATION) {
            throw new SecurityException("Only ORGANIZATION accounts can submit organization credentials");
        }

        if (!"application/pdf".equals(contentType) && !"image/jpeg".equals(contentType)) {
            throw new IllegalArgumentException("Invalid content type. Only application/pdf and image/jpeg are allowed.");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum allowed size of 10MB.");
        }

        String checksum = fileStorageService.computeChecksum(content);
        boolean duplicateFlag = orgDocumentRepository.existsByChecksum(checksum);
        String filePath = fileStorageService.store(content, "org-credentials");

        OrganizationCredentialDocument doc = new OrganizationCredentialDocument();
        doc.setAccountId(accountId);
        doc.setFileName(fileName);
        doc.setContentType(contentType);
        doc.setFileSize(fileSize);
        doc.setFilePath(filePath);
        doc.setChecksum(checksum);
        doc.setDuplicateFlag(duplicateFlag);
        doc.setStatus("UNDER_REVIEW");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        OrganizationCredentialDocument saved = orgDocumentRepository.save(doc);

        auditService.log(accountId, null, "ORG_CREDENTIAL_SUBMITTED",
                "OrganizationCredentialDocument", saved.getId().toString(),
                null, "UNDER_REVIEW", null, null);

        return saved;
    }

    public Map<String, List<?>> getVerificationQueue() {
        List<PersonVerification> personQueue = personVerificationRepository.findByStatus("UNDER_REVIEW");
        List<OrganizationCredentialDocument> orgQueue = orgDocumentRepository.findByStatus("UNDER_REVIEW");

        Map<String, List<?>> queue = new HashMap<>();
        queue.put("personVerifications", personQueue);
        queue.put("orgDocuments", orgQueue);
        return queue;
    }

    @Transactional
    public PersonVerification decidePersonVerification(Long verificationId, String decision,
                                                       String reviewNote, Long actorId, String actorRole) {
        if (!"APPROVE".equals(decision) && !"DENY".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE or DENY");
        }

        PersonVerification pv = personVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Person verification not found: " + verificationId));

        String beforeStatus = pv.getStatus();
        String newStatus = "APPROVE".equals(decision) ? "APPROVED" : "DENIED";
        pv.setStatus(newStatus);
        pv.setReviewedBy(actorId);
        pv.setReviewNote(reviewNote);
        pv.setUpdatedAt(LocalDateTime.now());

        PersonVerification saved = personVerificationRepository.save(pv);

        auditService.log(actorId, actorRole, "PERSON_VERIFICATION_DECIDED",
                "PersonVerification", saved.getId().toString(),
                beforeStatus, newStatus, decision, null);

        return saved;
    }

    @Transactional
    public OrganizationCredentialDocument decideOrgDocument(Long documentId, String decision,
                                                            String reviewNote, Long actorId, String actorRole) {
        if (!"APPROVE".equals(decision) && !"DENY".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE or DENY");
        }

        OrganizationCredentialDocument doc = orgDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Organization credential document not found: " + documentId));

        String beforeStatus = doc.getStatus();
        String newStatus = "APPROVE".equals(decision) ? "APPROVED" : "DENIED";
        doc.setStatus(newStatus);
        doc.setReviewedBy(actorId);
        doc.setReviewNote(reviewNote);
        doc.setUpdatedAt(LocalDateTime.now());

        OrganizationCredentialDocument saved = orgDocumentRepository.save(doc);

        auditService.log(actorId, actorRole, "ORG_CREDENTIAL_DECIDED",
                "OrganizationCredentialDocument", saved.getId().toString(),
                beforeStatus, newStatus, decision, null);

        return saved;
    }

    public String getDecryptedDob(Long verificationId) {
        PersonVerification pv = personVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Person verification not found: " + verificationId));
        return encryptionService.decrypt(pv.getDobEncrypted());
    }
}
