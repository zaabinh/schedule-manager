package vn.edu.school.schedule.task.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.school.schedule.shared.api.ApiException;

@Component
public class TaskAttachmentFileValidator {
    private static final Map<String, Set<String>> MIME_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/csv", "application/csv")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")));
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] OLE = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};

    public ValidatedFile validate(MultipartFile file, long maxFileSize) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) throw invalid("FILE_EMPTY", "Tệp tải lên đang trống.");
        if (file.getSize() > maxFileSize) throw invalid("FILE_TOO_LARGE", "Tệp vượt quá dung lượng cho phép.");
        String name = normalizeName(file.getOriginalFilename());
        String extension = extension(name);
        Set<String> allowedMimeTypes = MIME_TYPES.get(extension);
        if (allowedMimeTypes == null) throw invalid("FILE_TYPE_NOT_ALLOWED", "Định dạng tệp không được hỗ trợ.");
        String contentType = normalizeMime(file.getContentType());
        if (!allowedMimeTypes.contains(contentType)) throw invalid("FILE_MIME_INVALID", "Content-Type của tệp không hợp lệ.");
        try {
            if (!matchesContent(file, extension)) throw invalid("FILE_SIGNATURE_INVALID", "Nội dung tệp không khớp với định dạng đã khai báo.");
        } catch (IOException exception) {
            throw invalid("FILE_READ_FAILED", "Không thể kiểm tra nội dung tệp tải lên.");
        }
        return new ValidatedFile(name, extension, contentType);
    }

    private boolean matchesContent(MultipartFile file, String extension) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(8192); }
        return switch (extension) {
            case "pdf" -> startsWith(header, PDF);
            case "png" -> startsWith(header, PNG);
            case "jpg", "jpeg" -> header.length >= 3 && (header[0] & 0xff) == 0xff
                    && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "doc", "xls", "ppt" -> startsWith(header, OLE);
            case "docx" -> isZip(header) && zipContains(file, "word/");
            case "xlsx" -> isZip(header) && zipContains(file, "xl/");
            case "pptx" -> isZip(header) && zipContains(file, "ppt/");
            case "zip" -> isZip(header);
            case "txt", "csv" -> isUtf8Text(header);
            default -> false;
        };
    }

    private boolean zipContains(MultipartFile file, String prefix) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 1000) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith(prefix)) return true;
            }
            return false;
        }
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) { return false; }
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && ((bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6)
                || (bytes[2] == 7 && bytes[3] == 8));
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }

    private String normalizeName(String raw) {
        String value = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFC)
                .replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
        if (value.isBlank() || value.length() > 255) throw invalid("FILE_NAME_INVALID", "Tên tệp không hợp lệ hoặc quá dài.");
        return value;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 1 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String value) {
        if (value == null) return "";
        int parameters = value.indexOf(';');
        return (parameters < 0 ? value : value.substring(0, parameters)).trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public record ValidatedFile(String originalName, String extension, String contentType) { }
}
