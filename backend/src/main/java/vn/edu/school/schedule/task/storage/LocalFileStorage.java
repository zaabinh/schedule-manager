package vn.edu.school.schedule.task.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.task-attachments.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(@Value("${app.task-attachments.local-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile upload(String storageKey, InputStream content) {
        Path target = resolve(storageKey);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream source = content; DigestInputStream hashing = new DigestInputStream(source, digest)) {
                size = Files.copy(hashing, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            return new StoredFile(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            throw new FileStorageException("Không thể lưu tệp vào kho lưu trữ.", exception);
        }
    }

    @Override
    public Resource download(String storageKey) {
        Path target = resolve(storageKey);
        try {
            if (!Files.isRegularFile(target)) throw new FileStorageException("Không tìm thấy tệp trong kho lưu trữ.");
            return new UrlResource(target.toUri());
        } catch (IOException exception) {
            throw new FileStorageException("Không thể đọc tệp từ kho lưu trữ.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.delete(resolve(storageKey));
        } catch (IOException exception) {
            throw new FileStorageException("Không thể xóa tệp khỏi kho lưu trữ.", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\\') >= 0) {
            throw new FileStorageException("Storage key không hợp lệ.");
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new FileStorageException("Storage key nằm ngoài thư mục được phép.");
        }
        return target;
    }
}
