package com.tuganire.storage;

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

    /** S3 bucket name. */
    @NotBlank(message = "S3 bucket name must be configured (app.storage.s3.bucket-name)")
    private String bucketName;

    /** Optional key prefix prepended to every object (e.g. "uploads"). Empty = no prefix. */
    private String prefix = "";
}
