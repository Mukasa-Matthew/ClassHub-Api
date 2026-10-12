package com.classhub.courseunit;

import com.classhub.academicclass.AcademicClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "course_units")
public class CourseUnit {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "internal_code", nullable = false, updatable = false, length = 20)
    private String internalCode;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "lecturer_name", length = 200)
    private String lecturerName;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cover_image_storage_key", length = 256)
    private String coverImageStorageKey;

    @Column(name = "cover_image_original_name", length = 255)
    private String coverImageOriginalName;

    @Column(name = "cover_image_content_type", length = 100)
    private String coverImageContentType;

    @Column(name = "cover_image_size_bytes")
    private Long coverImageSizeBytes;

    @Column(name = "cover_image_updated_at")
    private Instant coverImageUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_class_id", nullable = false, updatable = false)
    private AcademicClass academicClass;

    protected CourseUnit() {
    }

    public CourseUnit(
            AcademicClass academicClass,
            String internalCode,
            String code,
            String name,
            String normalizedName,
            String lecturerName,
            String description,
            boolean active) {
        this.academicClass = academicClass;
        this.internalCode = internalCode;
        this.code = code;
        this.name = name;
        this.normalizedName = normalizedName;
        this.lecturerName = lecturerName;
        this.description = description;
        this.active = active;
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

    public String getInternalCode() {
        return internalCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getLecturerName() {
        return lecturerName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCoverImageStorageKey() {
        return coverImageStorageKey;
    }

    public String getCoverImageOriginalName() {
        return coverImageOriginalName;
    }

    public String getCoverImageContentType() {
        return coverImageContentType;
    }

    public Long getCoverImageSizeBytes() {
        return coverImageSizeBytes;
    }

    public Instant getCoverImageUpdatedAt() {
        return coverImageUpdatedAt;
    }

    public boolean hasCoverImage() {
        return coverImageStorageKey != null && !coverImageStorageKey.isBlank();
    }

    public void updateDetails(String code, String name, String normalizedName, String lecturerName, String description) {
        this.code = code;
        this.name = name;
        this.normalizedName = normalizedName;
        this.lecturerName = lecturerName;
        this.description = description;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCoverImage(String storageKey, String originalName, String contentType, long sizeBytes, Instant updatedAt) {
        this.coverImageStorageKey = storageKey;
        this.coverImageOriginalName = originalName;
        this.coverImageContentType = contentType;
        this.coverImageSizeBytes = sizeBytes;
        this.coverImageUpdatedAt = updatedAt;
    }

    public void clearCoverImage() {
        this.coverImageStorageKey = null;
        this.coverImageOriginalName = null;
        this.coverImageContentType = null;
        this.coverImageSizeBytes = null;
        this.coverImageUpdatedAt = null;
    }
}
