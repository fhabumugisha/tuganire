package com.tuganire.shared.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.storage.s3")
@Validated
@Getter
@Setter
public class AwsS3BucketProperties {

    @NotBlank(message = "S3 bucket name must be configured")
    private String bucketName;

    private String region;
}
