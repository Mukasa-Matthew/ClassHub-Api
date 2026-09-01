package com.classhub.academicclass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_classes")
public class AcademicClass {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "programme_name", length = 200)
    private String programmeName;

    @Column(name = "programme_code", length = 50)
    private String programmeCode;

    @Column(name = "join_code", nullable = false, length = 16)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AcademicClassStatus status;

    @Column(name = "semester_name", length = 200)
    private String semesterName;

    @Column(name = "semester_start_date")
    private LocalDate semesterStartDate;

    @Column(name = "semester_end_date")
    private LocalDate semesterEndDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicClass() {
    }

    public AcademicClass(
            String name,
            String programmeName,
            String programmeCode,
            String joinCode,
            AcademicClassStatus status) {
        this.name = name;
        this.programmeName = programmeName;
        this.programmeCode = programmeCode;
        this.joinCode = joinCode;
        this.status = status;
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

    public String getName() {
        return name;
    }

    public String getProgrammeName() {
        return programmeName;
    }

    public String getProgrammeCode() {
        return programmeCode;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public AcademicClassStatus getStatus() {
        return status;
    }

    public String getSemesterName() {
        return semesterName;
    }

    public LocalDate getSemesterStartDate() {
        return semesterStartDate;
    }

    public LocalDate getSemesterEndDate() {
        return semesterEndDate;
    }

    public boolean isSemesterTimelineConfigured() {
        return semesterStartDate != null && semesterEndDate != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String name,
            String programmeName,
            String programmeCode,
            AcademicClassStatus status) {
        this.name = name;
        this.programmeName = programmeName;
        this.programmeCode = programmeCode;
        this.status = status;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    public void updateSemesterTimeline(String semesterName, LocalDate startDate, LocalDate endDate) {
        this.semesterName = semesterName;
        this.semesterStartDate = startDate;
        this.semesterEndDate = endDate;
    }
}
