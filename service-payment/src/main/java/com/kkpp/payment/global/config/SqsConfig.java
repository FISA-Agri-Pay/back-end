package com.kkpp.payment.global.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "sqs")
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(
            @Value("${cloud.aws.sqs.region:ap-northeast-2}") String region,
            @Value("${cloud.aws.sqs.endpoint:}") String endpoint
    ) {
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
