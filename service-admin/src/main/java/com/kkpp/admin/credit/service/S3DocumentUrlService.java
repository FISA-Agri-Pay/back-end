package com.kkpp.admin.credit.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@Profile("prod | dev | s3")
public class S3DocumentUrlService implements DocumentUrlService {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration expiry;

    public S3DocumentUrlService(
            S3Presigner s3Presigner,
            @Value("${credit-docs.s3.bucket}") String bucket,
            @Value("${credit-docs.s3.presigned-url-expiry-minutes:15}") int expiryMinutes
    ) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.expiry = Duration.ofMinutes(expiryMinutes);
    }

    @Override
    public String resolve(String s3Key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(b -> b.bucket(bucket).key(s3Key))
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}