"use client";

import { Download, FileText, Trash2 } from "lucide-react";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { taskService } from "@/services";
import type { Task, TaskAttachment } from "@/types/domain";
import { Button } from "@/components/ui/button";
import { AttachmentPicker } from "@/components/task/attachment-picker";
import { formatFileSize, toUploadItems, validateAttachmentFiles, type UploadItem } from "@/components/task/attachment-utils";

export function TaskAttachments({ task, admin = false }: { task: Task; admin?: boolean }) {
  const client = useQueryClient();
  const [expanded, setExpanded] = useState(!admin);
  const [uploads, setUploads] = useState<UploadItem[]>([]);
  const [error, setError] = useState<string>();
  const query = useQuery({ queryKey: ["tasks", task.id, "attachments"], queryFn: () => taskService.listAttachments(task.id), enabled: expanded && (admin || (task.attachmentCount ?? 0) > 0) });
  const attachments = query.data ?? [];
  if (!admin && (task.attachmentCount ?? 0) === 0) return null;
  if (admin && !expanded) return <div className="mt-4 border-t pt-4"><Button type="button" variant="secondary" size="sm" onClick={() => setExpanded(true)}>Quản lý tệp đính kèm ({task.attachmentCount ?? 0})</Button></div>;

  async function upload(item: UploadItem) {
    setUploads((values) => values.map((value) => value.id === item.id ? { ...value, status: "UPLOADING", error: undefined } : value));
    try {
      await taskService.uploadAttachment(task.id, item.file);
      setUploads((values) => values.filter((value) => value.id !== item.id));
      await Promise.all([client.invalidateQueries({ queryKey: ["tasks", task.id, "attachments"] }), client.invalidateQueries({ queryKey: ["tasks"] })]);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Không thể tải tệp lên.";
      setUploads((values) => values.map((value) => value.id === item.id ? { ...value, status: "ERROR", error: message } : value));
    }
  }

  function addFiles(files: File[]) {
    setError(undefined);
    const validation = validateAttachmentFiles(uploads.map((item) => item.file), files, attachments.length);
    if (validation) { setError(validation); return; }
    const items = toUploadItems(files);
    setUploads((values) => [...values, ...items]);
    items.forEach((item) => void upload(item));
  }

  async function remove(attachment: TaskAttachment) {
    if (!window.confirm(`Xóa tệp “${attachment.originalName}”?`)) return;
    setError(undefined);
    try {
      await taskService.deleteAttachment(attachment.id);
      await Promise.all([client.invalidateQueries({ queryKey: ["tasks", task.id, "attachments"] }), client.invalidateQueries({ queryKey: ["tasks"] })]);
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể xóa tệp."); }
  }

  return <div className="mt-4 border-t pt-4">
    <h4 className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">Tệp đính kèm</h4>
    {query.isLoading && <p className="text-sm text-slate-500">Đang tải danh sách tệp…</p>}
    {query.isError && <p role="alert" className="text-sm text-red-700">Không thể tải danh sách tệp đính kèm.</p>}
    {attachments.length > 0 && <ul className="space-y-2">
      {attachments.map((attachment) => <li key={attachment.id} className="flex flex-wrap items-center gap-2 rounded-lg bg-slate-50 px-3 py-2">
        <FileText size={17} className="text-[var(--primary)]"/><span className="min-w-0 flex-1 truncate text-sm font-semibold">{attachment.originalName}</span>
        <span className="text-xs text-slate-500">{formatFileSize(attachment.fileSize)}</span>
        <Button type="button" variant="secondary" size="sm" onClick={() => void taskService.downloadAttachment(attachment)}><Download size={14}/>Tải xuống</Button>
        {admin && <Button type="button" variant="ghost" size="sm" aria-label={`Xóa tệp ${attachment.originalName}`} onClick={() => void remove(attachment)}><Trash2 size={14}/>Xóa</Button>}
      </li>)}
    </ul>}
    {admin && <div className="mt-3"><AttachmentPicker items={uploads} error={error} onFiles={addFiles}
      onRemove={(id) => setUploads((values) => values.filter((item) => item.id !== id))}
      onRetry={(id) => { const item = uploads.find((value) => value.id === id); if (item) void upload(item); }}/></div>}
  </div>;
}
