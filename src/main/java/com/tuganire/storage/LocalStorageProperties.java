package com.tuganire.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.local")
@Getter
@Setter
public class LocalStorageProperties {

    /** Local directory for the file-system fallback. Created on first write. */
    private String directory = System.getProperty("java.io.tmpdir") + "/app-storage";
}
