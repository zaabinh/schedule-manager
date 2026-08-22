package vn.edu.school.schedule.task.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void uploadDownloadDelete_usesGeneratedKeyAndChecksum() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory.toString());
        UUID taskId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String key = "tasks/" + taskId + "/" + attachmentId + ".txt";
        byte[] content = "Nội dung tiếng Việt".getBytes(StandardCharsets.UTF_8);

        FileStorage.StoredFile stored = storage.upload(key, new ByteArrayInputStream(content));

        assertThat(stored.storageKey()).isEqualTo(key);
        assertThat(stored.fileSize()).isEqualTo(content.length);
        assertThat(stored.checksum()).matches("[0-9a-f]{64}");
        assertThat(storage.download(key).getContentAsByteArray()).isEqualTo(content);
        assertThat(Files.exists(temporaryDirectory.resolve(key))).isTrue();

        storage.delete(key);
        assertThat(Files.exists(temporaryDirectory.resolve(key))).isFalse();
        assertThatThrownBy(() -> storage.download(key)).isInstanceOf(FileStorageException.class);
    }

    @Test
    void traversalKey_isRejected() {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory.toString());
        assertThatThrownBy(() -> storage.upload("../../outside.txt", new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> storage.download("tasks\\..\\secret.txt"))
                .isInstanceOf(FileStorageException.class);
    }
}
