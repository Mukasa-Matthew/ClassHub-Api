package com.classhub.courseunit;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.storage.FileStorage;
import com.classhub.user.UserRole;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
public class CourseUnitCoverImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "scr", "js", "jar", "sh", "ps1", "dll", "vbs", "svg");

    private static final int MAX_DIMENSION = 6000;

    private final CourseUnitRepository courseUnitRepository;
    private final FileStorage fileStorage;
    private final AuditService auditService;
    private final long maxCoverBytes;

    public CourseUnitCoverImageService(
            CourseUnitRepository courseUnitRepository,
            FileStorage fileStorage,
            AuditService auditService,
            @Value("${classhub.storage.max-course-unit-cover-size:5MB}") String maxCoverSize) {
        this.courseUnitRepository = courseUnitRepository;
        this.fileStorage = fileStorage;
        this.auditService = auditService;
        this.maxCoverBytes = DataSize.parse(maxCoverSize).toBytes();
    }

    @Transactional
    public CourseUnitResponse upload(UUID courseUnitId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidCover("file is required");
        }

        CourseUnit unit = requireCourseUnit(courseUnitId);
        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        validateImageType(originalFileName, contentType);

        long size = file.getSize();
        if (size <= 0) {
            throw invalidCover("file is empty");
        }
        if (size > maxCoverBytes) {
            throw new ApplicationException(
                    ErrorCodes.ATTACHMENT_TOO_LARGE,
                    "Cover image exceeds the maximum allowed size",
                    HttpStatus.BAD_REQUEST);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw storageFailure("Failed to read cover image");
        }

        validateImageDimensions(bytes);

        String extension = extensionFor(originalFileName);
        String storageKey = "course-units/" + courseUnitId + "/cover/" + UUID.randomUUID() + extension;
        String previousStorageKey = unit.getCoverImageStorageKey();

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            fileStorage.store(storageKey, in, size);
        } catch (IOException ex) {
            throw storageFailure("Failed to store cover image");
        }

        try {
            Instant now = Instant.now();
            unit.setCoverImage(storageKey, originalFileName, contentType, size, now);
            CourseUnit saved = courseUnitRepository.saveAndFlush(unit);
            if (previousStorageKey != null && !previousStorageKey.equals(storageKey)) {
                deleteStorageQuietly(previousStorageKey);
            }
            auditService.record(
                    AuditAction.COURSE_UNIT_COVER_UPDATED,
                    AuditEntityTypes.COURSE_UNIT,
                    saved.getId(),
                    "Updated cover image for course unit " + saved.getName());
            return CourseUnitResponse.from(saved);
        } catch (RuntimeException ex) {
            deleteStorageQuietly(storageKey);
            throw ex;
        }
    }

    @Transactional
    public CourseUnitResponse remove(UUID courseUnitId) {
        CourseUnit unit = requireCourseUnit(courseUnitId);
        if (!unit.hasCoverImage()) {
            throw coverNotFound();
        }
        String storageKey = unit.getCoverImageStorageKey();
        unit.clearCoverImage();
        CourseUnit saved = courseUnitRepository.saveAndFlush(unit);
        deleteStorageQuietly(storageKey);
        auditService.record(
                AuditAction.COURSE_UNIT_COVER_REMOVED,
                AuditEntityTypes.COURSE_UNIT,
                saved.getId(),
                "Removed cover image for course unit " + saved.getName());
        return CourseUnitResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(UUID courseUnitId) {
        CourseUnit unit = requireAccessibleCourseUnit(courseUnitId);
        if (!unit.hasCoverImage()) {
            throw coverNotFound();
        }

        InputStream stream = fileStorage.open(unit.getCoverImageStorageKey());
        InputStreamResource body = new InputStreamResource(stream);

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(unit.getCoverImageOriginalName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(unit.getCoverImageContentType()))
                .contentLength(unit.getCoverImageSizeBytes())
                .body(body);
    }

    private CourseUnit requireCourseUnit(UUID id) {
        return courseUnitRepository.findById(id).orElseThrow(CourseUnitService::notFound);
    }

    private CourseUnit requireAccessibleCourseUnit(UUID id) {
        CourseUnit unit = requireCourseUnit(id);
        if (currentRole() == UserRole.STUDENT && !unit.isActive()) {
            throw CourseUnitService.notFound();
        }
        return unit;
    }

    private static void validateImageDimensions(byte[] bytes) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInputStream == null) {
                throw invalidCover("file is not a valid image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw invalidCover("file is not a valid image");
            }
            ImageReader reader = readers.next();
            reader.setInput(imageInputStream);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            reader.dispose();
            if (width <= 0 || height <= 0) {
                throw invalidCover("image dimensions are invalid");
            }
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw invalidCover("image dimensions exceed the maximum allowed size");
            }
        } catch (IOException ex) {
            throw invalidCover("file is not a valid image");
        }
    }

    static String sanitizeOriginalFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw invalidCover("filename is required");
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw invalidCover("filename is invalid");
        }
        if (name.indexOf('\0') >= 0 || name.chars().anyMatch(ch -> ch < 32)) {
            throw invalidCover("filename contains illegal characters");
        }
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw invalidCover("content type is required");
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized;
    }

    private static void validateImageType(String fileName, String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw invalidCover("file type is not allowed");
        }
        rejectDangerousExecutableName(fileName);
        String extension = extensionFor(fileName);
        if (extension.isEmpty()) {
            throw invalidCover("file extension is required");
        }
        String ext = extension.substring(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw invalidCover("file extension is not allowed");
        }
    }

    private static void rejectDangerousExecutableName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String dangerous : DANGEROUS_EXTENSIONS) {
            if (lower.endsWith("." + dangerous) || lower.contains("." + dangerous + ".")) {
                throw invalidCover("file type is not allowed");
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

    private void deleteStorageQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            fileStorage.delete(storageKey);
        } catch (RuntimeException ex) {
            // best-effort cleanup
        }
    }

    private static ApplicationException invalidCover(String message) {
        return new ApplicationException(ErrorCodes.INVALID_ATTACHMENT, message, HttpStatus.BAD_REQUEST);
    }

    private static ApplicationException coverNotFound() {
        return new ApplicationException(
                ErrorCodes.COURSE_UNIT_COVER_NOT_FOUND,
                "Course unit cover image not found",
                HttpStatus.NOT_FOUND);
    }

    private static ApplicationException storageFailure(String message) {
        return new ApplicationException(
                ErrorCodes.ATTACHMENT_STORAGE_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static UserRole currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal.getRole();
    }
}
