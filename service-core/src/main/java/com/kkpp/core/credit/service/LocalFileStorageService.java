package com.kkpp.core.credit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Path UPLOAD_ROOT = Path.of("uploads", "credit-documents");
    private static final Path UPLOAD_ROOT_ABSOLUTE = UPLOAD_ROOT.toAbsolutePath().normalize();
    private static final Pattern SAFE_DIRECTORY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    @Override
    public String upload(String directory, MultipartFile file) {
        try {
            String safeDirectory = sanitizeDirectory(directory);
            Path directoryPath = UPLOAD_ROOT_ABSOLUTE.resolve(safeDirectory).normalize();
            validateUnderUploadRoot(directoryPath);
            Files.createDirectories(directoryPath);

            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + "-" + originalFilename;
            Path destination = directoryPath.resolve(storedFilename).normalize();
            validateUnderUploadRoot(destination);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination);
            }

            String fileUrl = UPLOAD_ROOT_ABSOLUTE.relativize(destination).toString().replace('\\', '/');
            log.info("[FileStorage] uploaded url={}", fileUrl);
            return fileUrl;
        } catch (IOException exception) {
            throw new IllegalStateException("파일 업로드에 실패했습니다.", exception);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            Path target = resolveStoredFile(fileUrl);
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.error("[FileStorage] rollback delete failed url={}", fileUrl, exception);
        } catch (IllegalArgumentException exception) {
            log.warn("[FileStorage] rejected unsafe rollback delete url={}", fileUrl);
        }
    }

    private String sanitizeDirectory(String directory) {
        if (directory == null || !SAFE_DIRECTORY_PATTERN.matcher(directory).matches()) {
            throw new IllegalArgumentException("Invalid upload directory.");
        }
        return directory;
    }

    private String sanitizeFilename(String filename) {
        String candidate = filename == null || filename.isBlank() ? "document" : filename;
        String normalized = candidate.replace('\\', '/');
        String lastSegment = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (lastSegment.isBlank() || lastSegment.equals(".") || lastSegment.equals("..")) {
            throw new IllegalArgumentException("Invalid upload filename.");
        }
        return lastSegment;
    }

    private Path resolveStoredFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid file URL.");
        }

        Path rawPath = Path.of(fileUrl);
        Path target = rawPath.isAbsolute()
                ? rawPath.normalize()
                : UPLOAD_ROOT_ABSOLUTE.resolve(rawPath).normalize();
        validateUnderUploadRoot(target);
        return target;
    }

    private void validateUnderUploadRoot(Path path) {
        if (!path.normalize().startsWith(UPLOAD_ROOT_ABSOLUTE)) {
            throw new IllegalArgumentException("Path escapes upload root.");
        }
    }
}
