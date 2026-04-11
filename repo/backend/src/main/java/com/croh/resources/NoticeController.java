package com.croh.resources;

import com.croh.security.SessionAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final ResourceService resourceService;

    public NoticeController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/{id}/print")
    public ResponseEntity<Map<String, Object>> getNotice(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        PrintableNotice notice = resourceService.getNotice(id);
        // Object-level authorization: notice must belong to the requesting user
        if (!notice.getAccountId().equals(actor.accountId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(Map.of(
                "id", notice.getId(),
                "noticeType", notice.getNoticeType(),
                "content", notice.getContent(),
                "createdAt", notice.getCreatedAt().toString()
        ));
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
