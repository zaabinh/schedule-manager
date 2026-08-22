export const ATTACHMENT_MAX_FILES = 10;
export const ATTACHMENT_MAX_FILE_SIZE = 20 * 1024 * 1024;
export const ATTACHMENT_MAX_TOTAL_SIZE = 100 * 1024 * 1024;
export const ATTACHMENT_EXTENSIONS = new Set([
  "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "jpg", "jpeg", "png", "zip",
]);

export type UploadStatus = "PENDING" | "UPLOADING" | "SUCCESS" | "ERROR";
export interface UploadItem { id: string; file: File; status: UploadStatus; error?: string }
type FileCandidate = Pick<File, "name" | "size">;

export function validateAttachmentFiles(current: FileCandidate[], incoming: FileCandidate[], existingCount = 0): string | undefined {
  if (existingCount + current.length + incoming.length > ATTACHMENT_MAX_FILES) return "Mỗi nhiệm vụ chỉ được tối đa 10 tệp.";
  for (const file of incoming) {
    const extension = file.name.includes(".") ? file.name.split(".").pop()?.toLowerCase() ?? "" : "";
    if (!ATTACHMENT_EXTENSIONS.has(extension)) return `Tệp “${file.name}” có định dạng không được hỗ trợ.`;
    if (file.size <= 0) return `Tệp “${file.name}” đang trống.`;
    if (file.size > ATTACHMENT_MAX_FILE_SIZE) return `Tệp “${file.name}” vượt quá 20 MB.`;
  }
  const total = [...current, ...incoming].reduce((sum, file) => sum + file.size, 0);
  if (total > ATTACHMENT_MAX_TOTAL_SIZE) return "Tổng dung lượng tệp vượt quá 100 MB.";
  return undefined;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function toUploadItems(files: File[]): UploadItem[] {
  return files.map((file) => ({ id: crypto.randomUUID(), file, status: "PENDING" }));
}
