package com.offerhunt.auth.domain.avatar;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.Resource;

public interface AvatarStorage {

    AvatarStoredObject save(
        UUID userId,
        String contentType,
        String originalFilename,
        long sizeBytes,
        InputStream data
    ) throws IOException;

    AvatarDownload open(String key) throws IOException;

    void delete(String key) throws IOException;

    record AvatarStoredObject(String key, String contentType, long sizeBytes) { }

    record AvatarDownload(Resource resource, String contentType, long sizeBytes) { }
}
