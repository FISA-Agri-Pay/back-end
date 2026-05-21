package com.kkpp.core.credit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Path UPLOAD_ROOT = Path.of("uploads", "credit-documents");

    @Override
    public String upload(String directory, MultipartFile file) {
        try {
            Files.createDirectories(UPLOAD_ROOT.resolve(directory));

            String originalFilename = StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "document" : file.getOriginalFilename()
            );
            String storedFilename = UUID.randomUUID() + "-" + originalFilename;
            Path destination = UPLOAD_ROOT.resolve(directory).resolve(storedFilename).normalize();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination);
            }

            String fileUrl = destination.toString().replace('\\', '/');
            log.info("[FileStorage] uploaded filename={} url={}", originalFilename, fileUrl);
            return fileUrl;
        } catch (IOException exception) {
            throw new IllegalStateException("파일 업로드에 실패했습니다.", exception);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            Files.deleteIfExists(Path.of(fileUrl));
        } catch (IOException exception) {
            log.error("[FileStorage] rollback delete failed url={}", fileUrl, exception);
        }
    }
}
