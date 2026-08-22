package vn.edu.school.schedule.task.attachment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.task.storage.FileStorage;

class TaskAttachmentServiceTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void metadataFailure_deletesStoredObjectBestEffort() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        FileStorage storage = mock(FileStorage.class);
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(taskId))).thenReturn(List.of(assigneeId));
        when(storage.upload(anyString(), any(InputStream.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return new FileStorage.StoredFile(key, 7, "a".repeat(64));
        });
        when(transaction.execute(any(TransactionCallback.class)))
                .thenThrow(new DataAccessResourceFailureException("metadata unavailable"));
        TaskAttachmentService service = new TaskAttachmentService(jdbc, transaction, storage,
                new TaskAttachmentFileValidator(), new TaskAttachmentProperties(10, 100, 1000), new ObjectMapper());
        var actor = new AuthenticatedUser(UUID.randomUUID(), "ADMIN", "session", "csrf");
        var file = new MockMultipartFile("file", "note.txt", "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(taskId, file, actor))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(storage).delete(org.mockito.ArgumentMatchers.matches(
                "tasks/" + taskId + "/[0-9a-f-]+\\.txt"));
    }
}
