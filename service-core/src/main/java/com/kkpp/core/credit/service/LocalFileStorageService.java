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
            log.info("파일 업로드를 완료했습니다. url={}", fileUrl);
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
            log.error("파일 롤백 삭제에 실패했습니다. url={}", fileUrl, exception);
        } catch (IllegalArgumentException exception) {
            log.warn("안전하지 않은 파일 롤백 삭제 요청을 거부했습니다. url={}", fileUrl);
        }
    }

    private String sanitizeDirectory(String directory) {
        if (directory == null || !SAFE_DIRECTORY_PATTERN.matcher(directory).matches()) {
            throw new IllegalArgumentException("업로드 디렉터리 형식이 올바르지 않습니다.");
        }
        return directory;
    }

    private String sanitizeFilename(String filename) {
        String candidate = filename == null || filename.isBlank() ? "document" : filename;
        String normalized = candidate.replace('\\', '/');
        String lastSegment = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (lastSegment.isBlank() || lastSegment.equals(".") || lastSegment.equals("..")) {
            throw new IllegalArgumentException("업로드 파일명이 올바르지 않습니다.");
        }
        return lastSegment;
    }

    private Path resolveStoredFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("파일 URL이 올바르지 않습니다.");
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
            throw new IllegalArgumentException("파일 경로가 업로드 루트를 벗어났습니다.");
        }
    }
}
