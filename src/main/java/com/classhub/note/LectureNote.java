package com.classhub.note;

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lecture_notes")
public class LectureNote {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_unit_id", nullable = false)
    private CourseUnit courseUnit;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "raw_content", nullable = false)
    private String rawContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LectureNoteStatus status;

    @Column(name = "lecture_started_at")
    private Instant lectureStartedAt;

    @Column(name = "lecture_ended_at")
    private Instant lectureEndedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LectureNote() {
    }

    public LectureNote(
            User student,
            CourseUnit courseUnit,
            String title,
            String rawContent,
            Instant lectureStartedAt) {
        this.student = student;
        this.courseUnit = courseUnit;
        this.title = title;
        this.rawContent = rawContent;
        this.status = LectureNoteStatus.ACTIVE;
        this.lectureStartedAt = lectureStartedAt;
        this.lectureEndedAt = null;
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

    public User getStudent() {
        return student;
    }

    public CourseUnit getCourseUnit() {
        return courseUnit;
    }

    public String getTitle() {
        return title;
    }

    public String getRawContent() {
        return rawContent;
    }

    public LectureNoteStatus getStatus() {
        return status;
    }

    public Instant getLectureStartedAt() {
        return lectureStartedAt;
    }

    public Instant getLectureEndedAt() {
        return lectureEndedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            CourseUnit courseUnit, String title, String rawContent, Instant lectureStartedAt) {
        this.courseUnit = courseUnit;
        this.title = title;
        this.rawContent = rawContent;
        this.lectureStartedAt = lectureStartedAt;
    }

    public void complete(Instant endedAt) {
        this.status = LectureNoteStatus.COMPLETED;
        this.lectureEndedAt = endedAt;
    }

    public void archive() {
        this.status = LectureNoteStatus.ARCHIVED;
    }
}
