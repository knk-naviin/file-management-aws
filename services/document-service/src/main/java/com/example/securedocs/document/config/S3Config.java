package com.example.securedocs.document.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import java.net.URI;

@Configuration
public class S3Config {
    @Bean
    S3Client s3Client(Region awsRegion, AwsCredentialsProvider credentialsProvider,
            @Value("${aws.s3.endpoint:}") String endpoint) {
        var builder = S3Client.builder().region(awsRegion).credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
