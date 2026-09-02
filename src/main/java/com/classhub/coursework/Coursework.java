package com.classhub.coursework;

import com.classhub.courseunit.CourseUnit;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coursework")
public class Coursework {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_unit_id", nullable = false)
    private CourseUnit courseUnit;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "instructions")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private CourseworkType type;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "weight", precision = 6, scale = 2)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private CourseworkSourceType sourceType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_label", length = 200)
    private String sourceLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CourseworkStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Coursework() {
    }

    public Coursework(
            CourseUnit courseUnit,
            String title,
            String description,
            String instructions,
            CourseworkType type,
            Instant issuedAt,
            Instant dueAt,
            BigDecimal weight,
            CourseworkSourceType sourceType,
            String sourceUrl,
            String sourceLabel,
            User createdBy) {
        this.courseUnit = courseUnit;
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.type = type;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
        this.weight = weight;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.sourceLabel = sourceLabel;
        this.status = CourseworkStatus.DRAFT;
        this.createdBy = createdBy;
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

    public CourseUnit getCourseUnit() {
        return courseUnit;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getInstructions() {
        return instructions;
    }

    public CourseworkType getType() {
        return type;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public CourseworkSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public CourseworkStatus getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
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

    public void updateDetails(
            CourseUnit courseUnit,
            String title,
            String description,
            String instructions,
            CourseworkType type,
            Instant issuedAt,
            Instant dueAt,
            BigDecimal weight,
            CourseworkSourceType sourceType,
            String sourceUrl,
            String sourceLabel) {
        this.courseUnit = courseUnit;
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.type = type;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
        this.weight = weight;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.sourceLabel = sourceLabel;
    }

    public void publish(Instant publishedAt) {
        this.status = CourseworkStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void cancel() {
        this.status = CourseworkStatus.CANCELLED;
    }

    public void archive() {
        this.status = CourseworkStatus.ARCHIVED;
    }
}
