package com.kkpp.core.credit.service;

import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;
import com.kkpp.core.global.logging.LogMaskingUtils;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
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

@Slf4j
@Service
@Profile("prod | s3 | dev")
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
            // S3 object key 원문은 저장소 접근 정보라 마스킹하고, 업로드 원인 분석에 필요한 메타데이터만 남깁니다.
            log.atInfo()
                    .addKeyValue("event", "credit.document.s3.upload.started")
                    .addKeyValue("directory", LogMaskingUtils.maskIdentifier(directory))
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(objectKey))
                    .addKeyValue("contentType", file.getContentType())
                    .addKeyValue("fileSize", file.getSize())
                    .addKeyValue("encryption", kmsKeyArn == null ? "AES256" : "AWS_KMS")
                    .log("S3 파일 업로드를 시작했습니다.");

            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize());

            if (kmsKeyArn != null) {
                requestBuilder.serverSideEncryption(ServerSideEncryption.AWS_KMS)
                        .ssekmsKeyId(kmsKeyArn);
            } else {
                requestBuilder.serverSideEncryption(ServerSideEncryption.AES256);
            }

            s3Client.putObject(requestBuilder.build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 업로드 성공 시에도 파일명이나 버킷명은 남기지 않습니다.
            log.atInfo()
                    .addKeyValue("event", "credit.document.s3.upload.completed")
                    .addKeyValue("directory", LogMaskingUtils.maskIdentifier(directory))
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(objectKey))
                    .addKeyValue("contentType", file.getContentType())
                    .addKeyValue("fileSize", file.getSize())
                    .log("S3 파일 업로드를 완료했습니다.");
            return objectKey;
        } catch (IOException | S3Exception e) {
            // AWS SDK 예외는 stack trace를 유지하고, 실패 구간은 PUT_OBJECT로 고정해 검색하기 쉽게 합니다.
            log.atError()
                    .addKeyValue("event", "credit.document.s3.upload.failed")
                    .addKeyValue("directory", LogMaskingUtils.maskIdentifier(directory))
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(objectKey))
                    .addKeyValue("contentType", file.getContentType())
                    .addKeyValue("fileSize", file.getSize())
                    .addKeyValue("failureState", "PUT_OBJECT")
                    .setCause(e)
                    .log("S3 파일 업로드에 실패했습니다.");
            throw new CreditException(CreditErrorCode.FILE_STORAGE_ERROR, objectKey);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            s3Client.deleteObject(b -> b.bucket(bucketName).key(fileUrl));
            log.atInfo()
                    .addKeyValue("event", "credit.document.s3.delete.completed")
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                    .log("S3 파일 삭제를 완료했습니다.");
        } catch (S3Exception e) {
            log.atError()
                    .addKeyValue("event", "credit.document.s3.delete.failed")
                    .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                    .addKeyValue("failureState", "DELETE_OBJECT")
                    .setCause(e)
                    .log("S3 파일 삭제에 실패했습니다.");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
