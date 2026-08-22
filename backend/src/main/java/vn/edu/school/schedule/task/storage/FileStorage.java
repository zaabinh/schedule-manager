package vn.edu.school.schedule.task.storage;

import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface FileStorage {
    StoredFile upload(String storageKey, InputStream content);
    Resource download(String storageKey);
    void delete(String storageKey);

    record StoredFile(String storageKey, long fileSize, String checksum) { }
}
