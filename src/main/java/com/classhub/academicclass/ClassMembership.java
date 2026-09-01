package com.classhub.academicclass;

import com.classhub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class_memberships")
public class ClassMembership {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_class_id", nullable = false, updatable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 32)
    private MembershipRole membershipRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MembershipStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClassMembership() {
    }

    public ClassMembership(
            AcademicClass academicClass,
            User user,
            MembershipRole membershipRole,
            MembershipStatus status,
            Instant requestedAt) {
        this.academicClass = academicClass;
        this.user = user;
        this.membershipRole = membershipRole;
        this.status = status;
        this.requestedAt = requestedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AcademicClass getAcademicClass() {
        return academicClass;
    }

    public User getUser() {
        return user;
    }

    public MembershipRole getMembershipRole() {
        return membershipRole;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void approve(User approver, Instant approvedAt) {
        this.status = MembershipStatus.ACTIVE;
        this.approvedAt = approvedAt;
        this.approvedBy = approver;
    }

    public void reject() {
        this.status = MembershipStatus.REJECTED;
        this.approvedAt = null;
        this.approvedBy = null;
    }

    public void deactivate() {
        this.status = MembershipStatus.INACTIVE;
    }

    public void reactivate(User approver, Instant approvedAt) {
        this.status = MembershipStatus.ACTIVE;
        this.approvedAt = approvedAt;
        this.approvedBy = approver;
    }

    public void assignAsClassRep(User approver, Instant approvedAt) {
        this.membershipRole = MembershipRole.CLASS_REP;
        approve(approver, approvedAt);
    }
}
