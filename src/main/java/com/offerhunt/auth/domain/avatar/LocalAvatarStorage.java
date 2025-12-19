package com.offerhunt.auth.domain.avatar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.avatar.storage", havingValue = "local", matchIfMissing = true)
public class LocalAvatarStorage implements AvatarStorage {

    private final Path baseDir;

    public LocalAvatarStorage(@Value("${app.avatar.local.base-dir:./data/avatars}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public AvatarStoredObject save(
        UUID userId,
        String contentType,
        String originalFilename,
        long sizeBytes,
        InputStream data
    ) throws IOException {

        Files.createDirectories(baseDir);

        String ext = fileExtByContentTypeOrName(contentType, originalFilename);
        String key = userId + "/" + UUID.randomUUID() + (ext != null ? ("." + ext) : "");
        Path target = resolveKeyToPath(key);

        Files.createDirectories(target.getParent());

        Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try (InputStream in = data) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort
            }
        }

        return new AvatarStoredObject(key, contentType, sizeBytes);
    }

    @Override
    public AvatarDownload open(String key) throws IOException {
        Path p = resolveKeyToPath(key);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new NoSuchFileException("avatar not found: " + key);
        }

        long size = Files.size(p);
        Resource r = new InputStreamResource(Files.newInputStream(p, StandardOpenOption.READ));
        // contentType хранится в БД, тут возвращаем null/unknown — контроллер возьмёт из user.avatarContentType
        return new AvatarDownload(r, null, size);
    }

    @Override
    public void delete(String key) throws IOException {
        Path p = resolveKeyToPath(key);
        Files.deleteIfExists(p);
        // можно удалить пустую директорию пользователя best-effort
        try {
            Path parent = p.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private Path resolveKeyToPath(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("invalid avatar key");
        }
        return resolved;
    }

    private String fileExtByContentTypeOrName(String contentType, String originalFilename) {
        if (contentType != null) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> extFromName(originalFilename);
            };
        }
        return extFromName(originalFilename);
    }

    private String extFromName(String originalFilename) {
        if (originalFilename == null) return null;
        int i = originalFilename.lastIndexOf('.');
        if (i < 0 || i == originalFilename.length() - 1) return null;
        String ext = originalFilename.substring(i + 1).toLowerCase(Locale.ROOT);
        return ext;
    }
}
