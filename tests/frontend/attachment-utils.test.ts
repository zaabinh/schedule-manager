import assert from "node:assert/strict";
import test from "node:test";
import {
  ATTACHMENT_MAX_FILE_SIZE,
  formatFileSize,
  validateAttachmentFiles,
} from "../../src/components/task/attachment-utils.ts";

const file = (name: string, size: number) => ({ name, size }) as File;

test("accepts supported files and formats their sizes", () => {
  assert.equal(validateAttachmentFiles([], [file("Mẫu báo cáo.docx", 245 * 1024)]), undefined);
  assert.equal(formatFileSize(245 * 1024), "245 KB");
});

test("blocks unsupported and executable extensions", () => {
  assert.match(validateAttachmentFiles([], [file("payload.exe", 10)]) ?? "", /không được hỗ trợ/);
  assert.match(validateAttachmentFiles([], [file("script.js", 10)]) ?? "", /không được hỗ trợ/);
});

test("blocks an oversized file", () => {
  assert.match(validateAttachmentFiles([], [file("large.pdf", ATTACHMENT_MAX_FILE_SIZE + 1)]) ?? "", /20 MB/);
});

test("blocks more than ten files and total size over 100 MB", () => {
  const ten = Array.from({ length: 10 }, (_, index) => file(`${index}.txt`, 1));
  assert.match(validateAttachmentFiles(ten, [file("extra.txt", 1)]) ?? "", /tối đa 10/);
  const existing = Array.from({ length: 5 }, (_, index) => file(`${index}.pdf`, 20 * 1024 * 1024));
  assert.match(validateAttachmentFiles(existing, [file("extra.pdf", 1)]) ?? "", /100 MB/);
});
