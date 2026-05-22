package com.tuganire.storage;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storage;

    @GetMapping("/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String key = path != null && path.startsWith("/files/") ? path.substring("/files/".length()) : path;
        if (key == null || key.isBlank() || key.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        byte[] bytes = storage.downloadFile(key);
        String mime = URLConnection.guessContentTypeFromName(key);
        MediaType type = mime != null ? MediaType.parseMediaType(mime) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).body(bytes);
    }
}
