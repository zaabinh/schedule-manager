"use client";

import { AlertTriangle, CheckCircle2, FileUp, RotateCcw, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatFileSize, type UploadItem } from "@/components/task/attachment-utils";

export function AttachmentPicker({ items, error, disabled, onFiles, onRemove, onRetry }: {
  items: UploadItem[];
  error?: string;
  disabled?: boolean;
  onFiles: (files: File[]) => void;
  onRemove: (id: string) => void;
  onRetry?: (id: string) => void;
}) {
  return <section aria-labelledby="attachment-picker-title">
    <h3 id="attachment-picker-title" className="field-label">Tệp đính kèm</h3>
    <label className="mt-1 flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center transition-colors hover:border-[var(--primary)]"
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => { event.preventDefault(); if (!disabled) onFiles(Array.from(event.dataTransfer.files)); }}>
      <FileUp className="mb-2 text-[var(--primary)]" size={24}/>
      <span className="text-sm font-semibold text-slate-800">Kéo thả tệp vào đây hoặc chọn tệp</span>
      <span className="mt-1 text-xs text-slate-500">PDF, Office, ảnh, TXT, CSV, ZIP · tối đa 20 MB/tệp</span>
      <input aria-label="Chọn tệp đính kèm" className="sr-only" type="file" multiple disabled={disabled}
             accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.jpg,.jpeg,.png,.zip"
             onChange={(event) => { onFiles(Array.from(event.target.files ?? [])); event.target.value = ""; }}/>
    </label>
    {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
    {items.length > 0 && <ul className="mt-3 space-y-2" aria-label="Tệp đã chọn">
      {items.map((item) => <li key={item.id} className="flex items-center gap-3 rounded-lg border bg-white px-3 py-2">
        {item.status === "SUCCESS" ? <CheckCircle2 className="shrink-0 text-emerald-600" size={18}/>
          : item.status === "ERROR" ? <AlertTriangle className="shrink-0 text-red-600" size={18}/>
          : <FileUp className="shrink-0 text-slate-500" size={18}/>} 
        <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-800">{item.file.name}</p>
          <p className="text-xs text-slate-500">{formatFileSize(item.file.size)} · {statusLabel(item)}</p>
          {item.error && <p className="text-xs text-red-700">{item.error}</p>}
        </div>
        {item.status === "ERROR" && onRetry && <Button type="button" variant="secondary" size="sm" onClick={() => onRetry(item.id)}><RotateCcw size={14}/>Thử lại</Button>}
        {item.status === "PENDING" && <button type="button" aria-label={`Bỏ tệp ${item.file.name}`} className="rounded-md p-2 text-slate-500 hover:bg-slate-100" onClick={() => onRemove(item.id)}><Trash2 size={16}/></button>}
      </li>)}
    </ul>}
  </section>;
}

function statusLabel(item: UploadItem) {
  if (item.status === "UPLOADING") return "Đang tải lên…";
  if (item.status === "SUCCESS") return "Đã tải lên";
  if (item.status === "ERROR") return "Tải lên thất bại";
  return "Sẵn sàng";
}
