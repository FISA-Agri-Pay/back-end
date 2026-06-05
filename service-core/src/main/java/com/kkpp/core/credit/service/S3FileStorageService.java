package com.kkpp.core.credit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@Profile("prod | s3")
public class S3FileStorageService implements FileStorageService {

    private static final String KEY_PREFIX = "credit-documents/";

    private final S3Client s3Client;
    private final String bucketName;
    private final String kmsKeyArn;

    public S3FileStorageService(
            S3Client s3Client,
            @Value("${cloud.aws.s3.bucket}") String bucketName,
            @Value("${cloud.aws.s3.kms-key-arn:}") String kmsKeyArn
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.kmsKeyArn = kmsKeyArn.isBlank() ? null : kmsKeyArn;
    }

    @Override
    public String upload(String directory, MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = KEY_PREFIX + directory + "/" + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS);

            if (kmsKeyArn != null) {
                requestBuilder.ssekmsKeyId(kmsKeyArn);
            }

            s3Client.putObject(requestBuilder.build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 파일 업로드를 완료했습니다. key={}", objectKey);
            return objectKey;
        } catch (IOException | S3Exception e) {
            throw new IllegalStateException("S3 파일 업로드에 실패했습니다.", e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            s3Client.deleteObject(b -> b.bucket(bucketName).key(fileUrl));
            log.info("S3 파일 롤백 삭제를 완료했습니다. key={}", fileUrl);
        } catch (S3Exception e) {
            log.error("S3 파일 롤백 삭제에 실패했습니다. key={}", fileUrl, e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}