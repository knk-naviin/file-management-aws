package com.example.securedocs.document.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration
public class KmsConfig {
    @Bean
    KmsClient kmsClient(Region awsRegion, AwsCredentialsProvider credentialsProvider,
            @Value("${aws.kms.endpoint:}") String endpoint) {
        var builder = KmsClient.builder().region(awsRegion).credentialsProvider(credentialsProvider);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
