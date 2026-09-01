package com.classhub.announcement;

import com.classhub.academicclass.AcademicClass;
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
@Table(name = "announcements")
public class Announcement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnnouncementStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_class_id", nullable = false, updatable = false)
    private AcademicClass academicClass;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Announcement() {
    }

    public Announcement(String title, String content, User createdBy, AcademicClass academicClass) {
        this.title = title;
        this.content = content;
        this.createdBy = createdBy;
        this.academicClass = academicClass;
        this.status = AnnouncementStatus.DRAFT;
        this.publishedAt = null;
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

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public AnnouncementStatus getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public AcademicClass getAcademicClass() {
        return academicClass;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateContent(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void publish(Instant publishedAt) {
        this.status = AnnouncementStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void archive() {
        this.status = AnnouncementStatus.ARCHIVED;
    }
}
