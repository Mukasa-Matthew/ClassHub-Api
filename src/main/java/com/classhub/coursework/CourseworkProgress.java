package com.classhub.coursework;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "coursework_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_coursework_progress_coursework_student",
                columnNames = {"coursework_id", "student_id"}))
public class CourseworkProgress {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coursework_id", nullable = false, updatable = false)
    private Coursework coursework;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 32)
    private CourseworkProgressStatus progressStatus;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseworkProgress() {
    }

    public CourseworkProgress(
            Coursework coursework,
            User student,
            CourseworkProgressStatus progressStatus,
            Instant completedAt) {
        this.coursework = coursework;
        this.student = student;
        this.progressStatus = progressStatus;
        this.completedAt = completedAt;
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

    public Coursework getCoursework() {
        return coursework;
    }

    public User getStudent() {
        return student;
    }

    public CourseworkProgressStatus getProgressStatus() {
        return progressStatus;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void applyStatus(CourseworkProgressStatus status, Instant now) {
        if (status == CourseworkProgressStatus.COMPLETED) {
            this.progressStatus = CourseworkProgressStatus.COMPLETED;
            if (this.completedAt == null) {
                this.completedAt = now;
            }
            return;
        }
        this.progressStatus = status;
        this.completedAt = null;
    }
}
