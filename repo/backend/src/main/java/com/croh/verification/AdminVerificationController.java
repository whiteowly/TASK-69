package com.croh.verification;

import com.croh.audit.AuditService;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import com.croh.verification.dto.VerificationDecisionRequest;
import com.croh.verification.dto.VerificationQueueItem;
import com.croh.files.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/verification")
public class AdminVerificationController {

    private final VerificationService verificationService;
    private final FileStorageService fileStorageService;
    private final OrganizationCredentialDocumentRepository orgDocRepository;
    private final AuditService auditService;

    public AdminVerificationController(VerificationService verificationService,
                                       FileStorageService fileStorageService,
                                       OrganizationCredentialDocumentRepository orgDocRepository,
                                       AuditService auditService) {
        this.verificationService = verificationService;
        this.fileStorageService = fileStorageService;
        this.orgDocRepository = orgDocRepository;
        this.auditService = auditService;
    }

    @GetMapping("/queue")
    @RequirePermission(Permission.REVIEW_VERIFICATION)
    public ResponseEntity<List<VerificationQueueItem>> getVerificationQueue() {
        SessionAccount actor = getSessionAccount();
        boolean canViewPii = actor.hasPermission(Permission.VIEW_PII);

        Map<String, List<?>> queue = verificationService.getVerificationQueue();
        List<VerificationQueueItem> items = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<PersonVerification> personVerifications = (List<PersonVerification>) queue.get("personVerifications");
        for (PersonVerification pv : personVerifications) {
            String dobMasked = "****-**-**";
            if (canViewPii) {
                dobMasked = verificationService.getDecryptedDob(pv.getId());
                // Audit PII read
                auditService.log(actor.accountId(), actor.activeRole().name(), "PII_VIEW",
                        "PersonVerification", pv.getId().toString(),
                        null, null, "DOB_ACCESS", UUID.randomUUID().toString());
            }
            items.add(new VerificationQueueItem(
                    "PERSON", pv.getId(), pv.getAccountId(), pv.getStatus(),
                    pv.getLegalName(), dobMasked,
                    null, null, null, false,
                    pv.getCreatedAt().atZone(ZoneOffset.UTC).toInstant()
            ));
        }

        @SuppressWarnings("unchecked")
        List<OrganizationCredentialDocument> orgDocs = (List<OrganizationCredentialDocument>) queue.get("orgDocuments");
        for (OrganizationCredentialDocument doc : orgDocs) {
            items.add(new VerificationQueueItem(
                    "ORG_DOCUMENT", doc.getId(), doc.getAccountId(), doc.getStatus(),
                    null, null,
                    doc.getFileName(), doc.getFileSize(), doc.getContentType(),
                    doc.isDuplicateFlag(),
                    doc.getCreatedAt().atZone(ZoneOffset.UTC).toInstant()
            ));
        }

        return ResponseEntity.ok(items);
    }

    @PostMapping("/person/{verificationId}/decision")
    @RequirePermission(Permission.REVIEW_VERIFICATION)
    public ResponseEntity<VerificationQueueItem> decidePersonVerification(
            @PathVariable Long verificationId,
            @Valid @RequestBody VerificationDecisionRequest request) {
        SessionAccount actor = getSessionAccount();

        PersonVerification pv = verificationService.decidePersonVerification(
                verificationId, request.decision(), request.reviewNote(),
                actor.accountId(), actor.activeRole().name());

        VerificationQueueItem item = new VerificationQueueItem(
                "PERSON", pv.getId(), pv.getAccountId(), pv.getStatus(),
                pv.getLegalName(), "****-**-**",
                null, null, null, false,
                pv.getCreatedAt().atZone(ZoneOffset.UTC).toInstant());
        return ResponseEntity.ok(item);
    }

    @PostMapping("/org-document/{documentId}/decision")
    @RequirePermission(Permission.REVIEW_VERIFICATION)
    public ResponseEntity<VerificationQueueItem> decideOrgDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody VerificationDecisionRequest request) {
        SessionAccount actor = getSessionAccount();

        OrganizationCredentialDocument doc = verificationService.decideOrgDocument(
                documentId, request.decision(), request.reviewNote(),
                actor.accountId(), actor.activeRole().name());

        VerificationQueueItem item = new VerificationQueueItem(
                "ORG_DOCUMENT", doc.getId(), doc.getAccountId(), doc.getStatus(),
                null, null,
                doc.getFileName(), doc.getFileSize(), doc.getContentType(),
                doc.isDuplicateFlag(),
                doc.getCreatedAt().atZone(ZoneOffset.UTC).toInstant());
        return ResponseEntity.ok(item);
    }

    /**
     * Downloads the decrypted credential document for admin review.
     * Requires both REVIEW_VERIFICATION and VIEW_PII since document content is sensitive.
     * Emits a PII_VIEW audit entry.
     */
    @GetMapping("/org-document/{documentId}/download")
    @RequirePermission({Permission.REVIEW_VERIFICATION, Permission.VIEW_PII})
    public ResponseEntity<byte[]> downloadOrgDocument(@PathVariable Long documentId) {
        SessionAccount actor = getSessionAccount();

        OrganizationCredentialDocument doc = orgDocRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        byte[] content = fileStorageService.read(doc.getFilePath());

        // Audit PII read for document content access
        auditService.log(actor.accountId(), actor.activeRole().name(), "PII_VIEW",
                "OrganizationCredentialDocument", documentId.toString(),
                null, null, "DOCUMENT_CONTENT_ACCESS", UUID.randomUUID().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, doc.getContentType());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"");
        headers.setContentLength(content.length);

        return ResponseEntity.ok().headers(headers).body(content);
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
