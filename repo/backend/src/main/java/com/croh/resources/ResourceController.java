package com.croh.resources;

import com.croh.resources.dto.ClaimRequest;
import com.croh.resources.dto.ClaimResponse;
import com.croh.resources.dto.DownloadRequest;
import com.croh.resources.dto.DownloadResponse;
import com.croh.resources.dto.ResourceRequest;
import com.croh.resources.dto.ResourceResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    @RequirePermission(Permission.PUBLISH_RESOURCE)
    public ResponseEntity<ResourceResponse> publishResource(@Valid @RequestBody ResourceRequest request) {
        SessionAccount actor = getSessionAccount();
        ResourceItem item = resourceService.publishResource(
                request.type(), request.title(), request.description(),
                request.inventoryCount(), request.fileVersion(),
                request.organizationId(),
                request.usagePolicyId(),
                request.status(), actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(toResponse(item));
    }

    @PostMapping("/upload")
    @RequirePermission(Permission.PUBLISH_RESOURCE)
    public ResponseEntity<ResourceResponse> uploadResource(
            @RequestPart("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "fileVersion", required = false) String fileVersion,
            @RequestParam("organizationId") String organizationId,
            @RequestParam(value = "usagePolicyId", required = false) Long usagePolicyId,
            @RequestParam(value = "status", required = false) String status) {
        SessionAccount actor = getSessionAccount();
        ResourceItem item = resourceService.uploadResource(
                title, description, fileVersion, organizationId, usagePolicyId, status,
                file, actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(toResponse(item));
    }

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> listResources() {
        List<ResourceResponse> resources = resourceService.listResources().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getResource(@PathVariable Long id) {
        ResourceItem item = resourceService.getResource(id);
        return ResponseEntity.ok(toResponse(item));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ClaimResponse> claimResource(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        ResourceService.ClaimResult result = resourceService.claimResource(
                id, actor.accountId());
        ClaimRecord claim = result.claimRecord();
        Long noticeId = result.notice() != null ? result.notice().getId() : null;
        return ResponseEntity.ok(new ClaimResponse(claim.getId(), claim.getResult(),
                claim.getReasonCode(), noticeId));
    }

    @PostMapping("/files/{id}/download")
    public ResponseEntity<DownloadResponse> downloadResource(@PathVariable Long id,
                                                              @RequestBody DownloadRequest request) {
        SessionAccount actor = getSessionAccount();
        ResourceService.DownloadResult result = resourceService.downloadResource(
                id, actor.accountId(), request.fileVersion());
        DownloadRecord record = result.downloadRecord();
        return ResponseEntity.ok(new DownloadResponse(record.getId(), record.getResult(),
                record.getReasonCode()));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getResourceFile(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        ResourceItem resource = resourceService.getResource(id);
        if (!"DOWNLOADABLE_FILE".equals(resource.getType())) {
            return ResponseEntity.badRequest().build();
        }
        byte[] fileData = resourceService.getResourceFileContent(id, actor.accountId());
        if (fileData == null) {
            return ResponseEntity.status(403).build();
        }
        String ct = resource.getContentType() != null ? resource.getContentType() : "application/octet-stream";
        String filename = "resource-" + id;
        if (resource.getContentType() != null) {
            filename += resource.getContentType().contains("pdf") ? ".pdf" : ".jpg";
        }
        return ResponseEntity.ok()
                .header("Content-Type", ct)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(fileData);
    }

    private ResourceResponse toResponse(ResourceItem r) {
        return new ResourceResponse(r.getId(), r.getType(), r.getTitle(), r.getDescription(),
                r.getInventoryCount(), r.getFileVersion(), r.getFileSize(), r.getContentType(),
                r.getUsagePolicyId(),
                r.getStatus(), r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
