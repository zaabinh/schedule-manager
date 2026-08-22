package vn.edu.school.schedule.task.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.school.schedule.shared.api.ApiException;

class TaskAttachmentFileValidatorTest {
    private final TaskAttachmentFileValidator validator = new TaskAttachmentFileValidator();

    @Test
    void validUnicodeDocx_preservesDisplayNameAndChecksPackage() throws Exception {
        var file = new MockMultipartFile("file", "Mẫu báo cáo tháng 8.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zip("word/document.xml", "<document/>"));
        var result = validator.validate(file, 20 * 1024 * 1024);
        assertThat(result.originalName()).isEqualTo("Mẫu báo cáo tháng 8.docx");
        assertThat(result.extension()).isEqualTo("docx");
    }

    @Test
    void executableInvalidMimeOversizeAndFakeSignature_areRejected() throws Exception {
        assertCode(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1}),
                100, "FILE_TYPE_NOT_ALLOWED");
        assertCode(new MockMultipartFile("file", "report.pdf", "application/octet-stream", "%PDF-1.7".getBytes()),
                100, "FILE_MIME_INVALID");
        assertCode(new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[101]),
                100, "FILE_TOO_LARGE");
        assertCode(new MockMultipartFile("file", "report.pdf", "application/pdf", "not a pdf".getBytes()),
                100, "FILE_SIGNATURE_INVALID");
    }

    @Test
    void pathSegmentsNeverBecomeTheDisplayOrStorageName() {
        var file = new MockMultipartFile("file", "../../Mẫu.txt", "text/plain",
                "an toàn".getBytes(StandardCharsets.UTF_8));
        assertThat(validator.validate(file, 100).originalName()).isEqualTo("Mẫu.txt");
    }

    private void assertCode(MockMultipartFile file, long maxSize, String code) {
        assertThatThrownBy(() -> validator.validate(file, maxSize))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private byte[] zip(String name, String value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(value.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
