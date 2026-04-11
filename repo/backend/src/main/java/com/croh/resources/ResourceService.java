package com.croh.resources;

import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.audit.AuditService;
import com.croh.files.FileStorageService;
import com.croh.rewards.ShippingAddress;
import com.croh.rewards.ShippingAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ResourceService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg");

    private final UsagePolicyRepository policyRepository;
    private final ResourceItemRepository resourceRepository;
    private final ClaimRecordRepository claimRecordRepository;
    private final DownloadRecordRepository downloadRecordRepository;
    private final PrintableNoticeRepository noticeRepository;
    private final ShippingAddressRepository addressRepository;
    private final RoleMembershipRepository roleMembershipRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public ResourceService(UsagePolicyRepository policyRepository,
                           ResourceItemRepository resourceRepository,
                           ClaimRecordRepository claimRecordRepository,
                           DownloadRecordRepository downloadRecordRepository,
                           PrintableNoticeRepository noticeRepository,
                           ShippingAddressRepository addressRepository,
                           RoleMembershipRepository roleMembershipRepository,
                           FileStorageService fileStorageService,
                           AuditService auditService) {
        this.policyRepository = policyRepository;
        this.resourceRepository = resourceRepository;
        this.claimRecordRepository = claimRecordRepository;
        this.downloadRecordRepository = downloadRecordRepository;
        this.noticeRepository = noticeRepository;
        this.addressRepository = addressRepository;
        this.roleMembershipRepository = roleMembershipRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public UsagePolicy createPolicy(String name, String scope, int maxActions, int windowDays,
                                     String resourceAction, Long actorId, String actorRole) {
        UsagePolicy policy = new UsagePolicy();
        policy.setName(name);
        policy.setScope(scope);
        policy.setMaxActions(maxActions);
        policy.setWindowDays(windowDays);
        policy.setResourceAction(resourceAction);
        policy.setCreatedAt(LocalDateTime.now());

        UsagePolicy saved = policyRepository.save(policy);

        auditService.log(actorId, actorRole, "POLICY_CREATED",
                "UsagePolicy", saved.getId().toString(),
                null, null, null, null);

        return saved;
    }

    @Transactional
    public ResourceItem publishResource(String type, String title, String description,
                                         Integer inventoryCount, String fileVersion,
                                         String organizationId,
                                         Long usagePolicyId, String status,
                                         Long actorId, String actorRole) {
        // Validate org scope
        validateOrganizationScope(actorId, organizationId, actorRole);

        ResourceItem item = new ResourceItem();
        item.setType(type);
        item.setOrganizationId(organizationId);
        item.setTitle(title);
        item.setDescription(description);
        item.setInventoryCount(inventoryCount);
        item.setFileVersion(fileVersion);
        item.setUsagePolicyId(usagePolicyId);
        item.setStatus(status != null ? status : "PUBLISHED");
        item.setCreatedBy(actorId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        ResourceItem saved = resourceRepository.save(item);

        auditService.log(actorId, actorRole, "RESOURCE_PUBLISHED",
                "ResourceItem", saved.getId().toString(),
                null, saved.getStatus(), null, null);

        return saved;
    }

    /**
     * Uploads a file for a DOWNLOADABLE_FILE resource via authenticated multipart.
     * Validates file type and size, stores via FileStorageService.
     */
    @Transactional
    public ResourceItem uploadResource(String title, String description, String fileVersion,
                                        String organizationId, Long usagePolicyId, String status,
                                        MultipartFile file,
                                        Long actorId, String actorRole) {
        validateOrganizationScope(actorId, organizationId, actorRole);

        // Validate file type
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Accepted: PDF, JPEG. Got: " + ct);
        }
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum of 10 MB. Got: " + file.getSize() + " bytes");
        }

        // Store file via managed storage
        String storedPath;
        try {
            storedPath = fileStorageService.store(file.getBytes(), "resources");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file: " + e.getMessage(), e);
        }

        ResourceItem item = new ResourceItem();
        item.setType("DOWNLOADABLE_FILE");
        item.setOrganizationId(organizationId);
        item.setTitle(title);
        item.setDescription(description);
        item.setFileVersion(fileVersion);
        item.setFilePath(storedPath);
        item.setFileSize(file.getSize());
        item.setContentType(ct);
        item.setUsagePolicyId(usagePolicyId);
        item.setStatus(status != null ? status : "PUBLISHED");
        item.setCreatedBy(actorId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        ResourceItem saved = resourceRepository.save(item);

        auditService.log(actorId, actorRole, "RESOURCE_PUBLISHED",
                "ResourceItem", saved.getId().toString(),
                null, saved.getStatus(), null, null);

        return saved;
    }

    public List<UsagePolicy> listPolicies() {
        return policyRepository.findAll();
    }

    public List<ResourceItem> listResources() {
        return resourceRepository.findByStatus("PUBLISHED");
    }

    public ResourceItem getResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + id));
    }

    /**
     * Claims a resource for the given account. Household-scoped limits are enforced
     * using the participant's approved primary shipping address as the offline household key.
     */
    @Transactional
    public ClaimResult claimResource(Long resourceId, Long accountId) {
        ResourceItem resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));

        // Derive household key from primary address
        String householdKey = deriveHouseholdKey(accountId);

        // Evaluate policy
        if (resource.getUsagePolicyId() != null) {
            UsagePolicy policy = policyRepository.findById(resource.getUsagePolicyId())
                    .orElse(null);
            if (policy != null && "CLAIM".equals(policy.getResourceAction())) {
                LocalDateTime windowStart = LocalDateTime.now().minusDays(policy.getWindowDays());
                long count;
                if ("HOUSEHOLD".equals(policy.getScope())) {
                    if (householdKey == null) {
                        throw new IllegalStateException(
                                "Household-scoped policy requires a primary shipping address. Please add an address first.");
                    }
                    count = claimRecordRepository.countByResourceIdAndHouseholdKeyAndResultAndCreatedAtAfter(
                            resourceId, householdKey, "ALLOWED", windowStart);
                } else {
                    count = claimRecordRepository.countByResourceIdAndAccountIdAndResultAndCreatedAtAfter(
                            resourceId, accountId, "ALLOWED", windowStart);
                }
                if (count >= policy.getMaxActions()) {
                    ClaimRecord denied = createClaimRecord(resourceId, accountId, householdKey,
                            "DENIED_POLICY", "POLICY_LIMIT_EXCEEDED");
                    auditService.log(accountId, null, "RESOURCE_CLAIM_DENIED",
                            "ClaimRecord", denied.getId().toString(),
                            null, "DENIED_POLICY", "POLICY_LIMIT_EXCEEDED", null);
                    return new ClaimResult(denied, null);
                }
            }
        }

        // Check inventory
        if (resource.getInventoryCount() != null && resource.getInventoryCount() <= 0) {
            ClaimRecord denied = createClaimRecord(resourceId, accountId, householdKey,
                    "DENIED_POLICY", "OUT_OF_STOCK");
            auditService.log(accountId, null, "RESOURCE_CLAIM_DENIED",
                    "ClaimRecord", denied.getId().toString(),
                    null, "DENIED_POLICY", "OUT_OF_STOCK", null);
            return new ClaimResult(denied, null);
        }

        // Decrement inventory
        if (resource.getInventoryCount() != null) {
            resource.setInventoryCount(resource.getInventoryCount() - 1);
            resource.setUpdatedAt(LocalDateTime.now());
            resourceRepository.save(resource);
        }

        ClaimRecord claim = createClaimRecord(resourceId, accountId, householdKey, "ALLOWED", null);

        // Create printable notice
        PrintableNotice notice = new PrintableNotice();
        notice.setAccountId(accountId);
        notice.setResourceId(resourceId);
        notice.setNoticeType("CLAIM_CONFIRMATION");
        notice.setContent("Resource claimed: " + resource.getTitle() + " (Claim ID: " + claim.getId() + ")");
        notice.setCreatedAt(LocalDateTime.now());
        PrintableNotice savedNotice = noticeRepository.save(notice);

        auditService.log(accountId, null, "RESOURCE_CLAIMED",
                "ClaimRecord", claim.getId().toString(),
                null, "ALLOWED", null, null);

        return new ClaimResult(claim, savedNotice);
    }

    @Transactional
    public DownloadResult downloadResource(Long resourceId, Long accountId, String fileVersion) {
        ResourceItem resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));

        // Evaluate policy
        if (resource.getUsagePolicyId() != null) {
            UsagePolicy policy = policyRepository.findById(resource.getUsagePolicyId())
                    .orElse(null);
            if (policy != null && "DOWNLOAD".equals(policy.getResourceAction())) {
                LocalDateTime windowStart = LocalDateTime.now().minusDays(policy.getWindowDays());
                long count = downloadRecordRepository.countByResourceIdAndAccountIdAndFileVersionAndResultAndCreatedAtAfter(
                        resourceId, accountId, fileVersion, "ALLOWED", windowStart);
                if (count >= policy.getMaxActions()) {
                    DownloadRecord denied = createDownloadRecord(resourceId, accountId, fileVersion,
                            "DENIED_POLICY", "POLICY_LIMIT_EXCEEDED");
                    auditService.log(accountId, null, "RESOURCE_DOWNLOAD_DENIED",
                            "DownloadRecord", denied.getId().toString(),
                            null, "DENIED_POLICY", "POLICY_LIMIT_EXCEEDED", null);
                    return new DownloadResult(denied);
                }
            }
        }

        DownloadRecord record = createDownloadRecord(resourceId, accountId, fileVersion, "ALLOWED", null);

        auditService.log(accountId, null, "RESOURCE_DOWNLOADED",
                "DownloadRecord", record.getId().toString(),
                null, "ALLOWED", null, null);

        return new DownloadResult(record);
    }

    public byte[] getResourceFileContent(Long resourceId, Long accountId) {
        List<DownloadRecord> records = downloadRecordRepository.findByResourceIdAndAccountId(resourceId, accountId);
        boolean hasAllowed = records.stream().anyMatch(r -> "ALLOWED".equals(r.getResult()));
        if (!hasAllowed) {
            return null;
        }
        ResourceItem resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        // Read file bytes through managed storage (encrypted at rest)
        if (resource.getFilePath() != null) {
            return fileStorageService.read(resource.getFilePath());
        }

        return null;
    }

    public PrintableNotice getNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("Notice not found: " + noticeId));
    }

    private ClaimRecord createClaimRecord(Long resourceId, Long accountId, String householdKey,
                                           String result, String reasonCode) {
        ClaimRecord record = new ClaimRecord();
        record.setResourceId(resourceId);
        record.setAccountId(accountId);
        record.setHouseholdKey(householdKey);
        record.setResult(result);
        record.setReasonCode(reasonCode);
        record.setCreatedAt(LocalDateTime.now());
        return claimRecordRepository.save(record);
    }

    private DownloadRecord createDownloadRecord(Long resourceId, Long accountId, String fileVersion,
                                                 String result, String reasonCode) {
        DownloadRecord record = new DownloadRecord();
        record.setResourceId(resourceId);
        record.setAccountId(accountId);
        record.setFileVersion(fileVersion);
        record.setResult(result);
        record.setReasonCode(reasonCode);
        record.setCreatedAt(LocalDateTime.now());
        return downloadRecordRepository.save(record);
    }

    /**
     * Derives the household key from the participant's primary shipping address.
     * Uses normalized city + state + zip as the offline household identifier.
     * Returns null if no primary address exists.
     */
    private String deriveHouseholdKey(Long accountId) {
        List<ShippingAddress> addresses = addressRepository.findByAccountId(accountId);
        ShippingAddress primary = addresses.stream()
                .filter(ShippingAddress::isPrimary)
                .findFirst()
                .orElse(null);
        if (primary == null) {
            return null;
        }
        // Normalize: city (lowercase) + state + zip
        return (primary.getCity().toLowerCase().trim() + "|" +
                primary.getStateCode().toUpperCase().trim() + "|" +
                primary.getZipCode().trim());
    }

    private void validateOrganizationScope(Long actorId, String organizationId, String actorRole) {
        if ("ADMIN".equals(actorRole)) {
            return;
        }
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("organizationId is required for resource publishing");
        }
        boolean hasScope = roleMembershipRepository.existsByAccountIdAndRoleTypeAndScopeIdAndStatus(
                actorId, "ORG_OPERATOR", organizationId, RoleMembership.RoleMembershipStatus.APPROVED);
        if (!hasScope) {
            throw new SecurityException(
                    "Not authorized for organization: " + organizationId);
        }
    }

    public record ClaimResult(ClaimRecord claimRecord, PrintableNotice notice) {}
    public record DownloadResult(DownloadRecord downloadRecord) {}
}
