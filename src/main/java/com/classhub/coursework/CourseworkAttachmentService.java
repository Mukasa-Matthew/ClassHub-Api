package com.classhub.coursework;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.storage.FileStorage;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourseworkAttachmentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/zip");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "jpg", "jpeg", "png", "webp", "zip");

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "scr", "js", "jar", "sh", "ps1", "dll", "vbs");

    private final CourseworkAttachmentRepository attachmentRepository;
    private final CourseworkRepository courseworkRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final AuditService auditService;
    private final long maxAttachmentBytes;

    public CourseworkAttachmentService(
            CourseworkAttachmentRepository attachmentRepository,
            CourseworkRepository courseworkRepository,
            UserRepository userRepository,
            FileStorage fileStorage,
            AuditService auditService,
            @Value("${classhub.storage.max-attachment-size}") String maxAttachmentSize) {
        this.attachmentRepository = attachmentRepository;
        this.courseworkRepository = courseworkRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.auditService = auditService;
        this.maxAttachmentBytes = DataSize.parse(maxAttachmentSize).toBytes();
    }

    @Transactional
    public CourseworkAttachmentResponse upload(UUID courseworkId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidAttachment("file is required");
        }

        Coursework coursework = requireCoursework(courseworkId);
        requireMutableForAttachment(coursework);

        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        validateType(originalFileName, contentType);

        long size = file.getSize();
        if (size <= 0) {
            throw invalidAttachment("file is empty");
        }
        if (size > maxAttachmentBytes) {
            throw new ApplicationException(
                    ErrorCodes.ATTACHMENT_TOO_LARGE,
                    "Attachment exceeds the maximum allowed size",
                    HttpStatus.BAD_REQUEST);
        }

        String storageKey = UUID.randomUUID() + extensionFor(originalFileName);
        User uploader = requireCurrentUser();

        try (InputStream in = file.getInputStream()) {
            fileStorage.store(storageKey, in, size);
        } catch (IOException ex) {
            throw new ApplicationException(
                    ErrorCodes.ATTACHMENT_STORAGE_ERROR,
                    "Failed to store attachment",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        try {
            CourseworkAttachment attachment = new CourseworkAttachment(
                    coursework, originalFileName, storageKey, contentType, size, uploader);
            CourseworkAttachment saved = attachmentRepository.saveAndFlush(attachment);
            auditService.record(
                    AuditAction.ATTACHMENT_UPLOADED,
                    AuditEntityTypes.ATTACHMENT,
                    saved.getId(),
                    "Uploaded attachment \""
                            + saved.getOriginalFileName()
                            + "\" to coursework "
                            + courseworkId);
            return CourseworkAttachmentResponse.from(saved);
        } catch (RuntimeException ex) {
            try {
                fileStorage.delete(storageKey);
            } catch (RuntimeException cleanupEx) {
                // best-effort cleanup after metadata failure
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<CourseworkAttachmentResponse> list(UUID courseworkId) {
        Coursework coursework = requireAccessibleCoursework(courseworkId);
        return attachmentRepository.findByCourseworkIdOrderByCreatedAtAsc(coursework.getId()).stream()
                .map(CourseworkAttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(UUID courseworkId, UUID attachmentId) {
        Coursework coursework = requireAccessibleCoursework(courseworkId);
        CourseworkAttachment attachment = attachmentRepository
                .findByIdAndCourseworkId(attachmentId, coursework.getId())
                .orElseThrow(this::notFound);

        InputStream stream = fileStorage.open(attachment.getStorageKey());
        InputStreamResource body = new InputStreamResource(stream);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getFileSize())
                .body(body);
    }

    @Transactional
    public void delete(UUID courseworkId, UUID attachmentId) {
        Coursework coursework = requireCoursework(courseworkId);
        requireMutableForAttachment(coursework);

        CourseworkAttachment attachment = attachmentRepository
                .findByIdAndCourseworkId(attachmentId, coursework.getId())
                .orElseThrow(this::notFound);

        String storageKey = attachment.getStorageKey();
        String originalFileName = attachment.getOriginalFileName();
        UUID deletedId = attachment.getId();
        fileStorage.delete(storageKey);
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();
        auditService.record(
                AuditAction.ATTACHMENT_DELETED,
                AuditEntityTypes.ATTACHMENT,
                deletedId,
                "Deleted attachment \"" + originalFileName + "\" from coursework " + courseworkId);
    }

    @Transactional(readOnly = true)
    public long countFor(UUID courseworkId) {
        return attachmentRepository.countByCourseworkId(courseworkId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> countsFor(java.util.Collection<UUID> courseworkIds) {
        java.util.HashMap<UUID, Long> counts = new java.util.HashMap<>();
        for (UUID id : courseworkIds) {
            counts.put(id, 0L);
        }
        if (courseworkIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : attachmentRepository.countGroupedByCourseworkId(courseworkIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Coursework requireCoursework(UUID courseworkId) {
        return courseworkRepository
                .findDetailedById(courseworkId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.COURSEWORK_NOT_FOUND,
                        "Coursework not found",
                        HttpStatus.NOT_FOUND));
    }

    private Coursework requireAccessibleCoursework(UUID courseworkId) {
        Coursework coursework = requireCoursework(courseworkId);
        if (currentRole() == UserRole.STUDENT && coursework.getStatus() != CourseworkStatus.PUBLISHED) {
            throw new ApplicationException(
                    ErrorCodes.COURSEWORK_NOT_FOUND,
                    "Coursework not found",
                    HttpStatus.NOT_FOUND);
        }
        return coursework;
    }

    private static void requireMutableForAttachment(Coursework coursework) {
        CourseworkStatus status = coursework.getStatus();
        if (status != CourseworkStatus.DRAFT && status != CourseworkStatus.PUBLISHED) {
            throw new ApplicationException(
                    ErrorCodes.ATTACHMENT_NOT_ALLOWED,
                    "Attachments can only be modified while coursework is DRAFT or PUBLISHED",
                    HttpStatus.CONFLICT);
        }
    }

    private User requireCurrentUser() {
        ClassHubUserDetails principal = currentPrincipal();
        return userRepository
                .findById(principal.getId())
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.UNAUTHENTICATED,
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED));
    }

    static String sanitizeOriginalFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw invalidAttachment("filename is required");
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw invalidAttachment("filename is invalid");
        }
        if (name.indexOf('\0') >= 0 || name.chars().anyMatch(ch -> ch < 32)) {
            throw invalidAttachment("filename contains illegal characters");
        }
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw invalidAttachment("content type is required");
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized;
    }

    private static void validateType(String fileName, String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw invalidAttachment("file type is not allowed");
        }
        rejectDangerousExecutableName(fileName);
        String extension = extensionFor(fileName);
        if (extension.isEmpty()) {
            throw invalidAttachment("file extension is required");
        }
        String ext = extension.substring(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw invalidAttachment("file extension is not allowed");
        }
    }

    private static void rejectDangerousExecutableName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String dangerous : DANGEROUS_EXTENSIONS) {
            if (lower.endsWith("." + dangerous) || lower.contains("." + dangerous + ".")) {
                throw invalidAttachment("file type is not allowed");
            }
        }
    }

    private static String extensionFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.contains("/") || ext.contains("\\") || ext.length() > 16) {
            return "";
        }
        return ext;
    }

    private static ApplicationException invalidAttachment(String message) {
        return new ApplicationException(ErrorCodes.INVALID_ATTACHMENT, message, HttpStatus.BAD_REQUEST);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                ErrorCodes.ATTACHMENT_NOT_FOUND,
                "Attachment not found",
                HttpStatus.NOT_FOUND);
    }

    private static UserRole currentRole() {
        return currentPrincipal().getRole();
    }

    private static ClassHubUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}
