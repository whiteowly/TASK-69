package com.croh.verification;

import com.croh.security.SessionAccount;
import com.croh.verification.dto.OrgDocumentResponse;
import com.croh.verification.dto.PersonVerificationRequest;
import com.croh.verification.dto.PersonVerificationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/person")
    public ResponseEntity<PersonVerificationResponse> submitPersonVerification(
            @Valid @RequestBody PersonVerificationRequest request) {
        SessionAccount actor = getSessionAccount();

        PersonVerification pv = verificationService.submitPersonVerification(
                actor.accountId(), request.legalName(), request.dateOfBirth());

        PersonVerificationResponse response = new PersonVerificationResponse(
                pv.getId(),
                pv.getStatus(),
                pv.getCreatedAt().atZone(ZoneOffset.UTC).toInstant()
        );
        return ResponseEntity.status(202).body(response);
    }

    @PostMapping("/org-documents")
    public ResponseEntity<OrgDocumentResponse> submitOrgDocument(
            @RequestParam("file") MultipartFile file) throws IOException {
        SessionAccount actor = getSessionAccount();

        OrganizationCredentialDocument doc = verificationService.submitOrgCredential(
                actor.accountId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getBytes()
        );

        OrgDocumentResponse response = new OrgDocumentResponse(
                doc.getId(),
                doc.getStatus(),
                doc.isDuplicateFlag()
        );
        return ResponseEntity.status(201).body(response);
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
