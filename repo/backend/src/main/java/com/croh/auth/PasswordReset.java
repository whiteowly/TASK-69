package com.croh.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset")
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_account_id", nullable = false)
    private Long targetAccountId;

    @Column(name = "identity_review_note", nullable = false, columnDefinition = "TEXT")
    private String identityReviewNote;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ISSUED";

    @Column(name = "temporary_secret", nullable = false, length = 255)
    private String temporarySecret;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PasswordReset() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(Long targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getIdentityReviewNote() {
        return identityReviewNote;
    }

    public void setIdentityReviewNote(String identityReviewNote) {
        this.identityReviewNote = identityReviewNote;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTemporarySecret() {
        return temporarySecret;
    }

    public void setTemporarySecret(String temporarySecret) {
        this.temporarySecret = temporarySecret;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
