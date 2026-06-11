package com.kkpp.core.credit.service;

import com.kkpp.core.global.logging.LogMaskingUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Profile("!prod & !s3")
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

            String storedFilename = UUID.randomUUID() + "." + extractExtension(file.getOriginalFilename());
            Path destination = directoryPath.resolve(storedFilename).normalize();
            validateUnderUploadRoot(destination);

            // 로컬 테스트에서도 원본 파일명은 저장/로그에 남기지 않고 UUID와 확장자만 사용합니다.
            log.atInfo()
                    .addKeyValue("event", "credit.document.local.upload.started")
                    .addKeyValue("directory", LogMaskingUtils.maskIdentifier(directory))
                    .addKeyValue("contentType", file.getContentType())
                    .addKeyValue("fileSize", file.getSize())
                    .log("로컬 파일 업로드를 시작했습니다.");

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination);
            }

            String fileUrl = UPLOAD_ROOT_ABSOLUTE.relativize(destination).toString().replace('\\', '/');
            log.atInfo()
                    .addKeyValue("event", "credit.document.local.upload.completed")
                    .addKeyValue("directory", LogMaskingUtils.maskIdentifier(directory))
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                    .addKeyValue("contentType", file.getContentType())
                    .addKeyValue("fileSize", file.getSize())
                    .log("로컬 파일 업로드를 완료했습니다.");
            return fileUrl;
        } catch (IOException exception) {
            log.atError()
                    .addKeyValue("event", "credit.document.local.upload.failed")
                    .addKeyValue("directory", LogMaskingUtils.maskIdentifier(directory))
                    .addKeyValue("contentType", file.getContentType())
                    .addKeyValue("fileSize", file.getSize())
                    .addKeyValue("failureState", "COPY_FILE")
                    .setCause(exception)
                    .log("로컬 파일 업로드에 실패했습니다.");
            throw new IllegalStateException("파일 업로드에 실패했습니다.", exception);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            Path target = resolveStoredFile(fileUrl);
            Files.deleteIfExists(target);
            log.atInfo()
                    .addKeyValue("event", "credit.document.local.delete.completed")
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                    .log("로컬 파일 삭제를 완료했습니다.");
        } catch (IOException exception) {
            log.atError()
                    .addKeyValue("event", "credit.document.local.delete.failed")
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                    .addKeyValue("failureState", "DELETE_FILE")
                    .setCause(exception)
                    .log("로컬 파일 삭제에 실패했습니다.");
        } catch (IllegalArgumentException exception) {
            log.atWarn()
                    .addKeyValue("event", "credit.document.local.delete.rejected")
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                    .addKeyValue("failureState", "INVALID_FILE_PATH")
                    .log("안전하지 않은 로컬 파일 삭제 요청을 거부했습니다.");
        }
    }

    private String sanitizeDirectory(String directory) {
        if (directory == null || !SAFE_DIRECTORY_PATTERN.matcher(directory).matches()) {
            throw new IllegalArgumentException("업로드 디렉터리 형식이 올바르지 않습니다.");
        }
        return directory;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        String normalized = filename.replace('\\', '/');
        String lastSegment = normalized.substring(normalized.lastIndexOf('/') + 1);
        return lastSegment.substring(lastSegment.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
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
