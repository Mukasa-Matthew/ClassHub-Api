package com.classhub.note;

import com.classhub.ai.AiNoteOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lecture_note_ai_outputs")
public class LectureNoteAiOutput {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_note_id", nullable = false, updatable = false)
    private LectureNote lectureNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 32)
    private AiNoteOperation operation;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "model_provider", length = 100)
    private String modelProvider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LectureNoteAiOutput() {
    }

    public LectureNoteAiOutput(
            LectureNote lectureNote,
            AiNoteOperation operation,
            String content,
            String modelProvider,
            String modelName) {
        this.lectureNote = lectureNote;
        this.operation = operation;
        this.content = content;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LectureNote getLectureNote() {
        return lectureNote;
    }

    public AiNoteOperation getOperation() {
        return operation;
    }

    public String getContent() {
        return content;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
