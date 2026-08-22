"use client";

import { ClipboardPlus } from "lucide-react";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { taskService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { TaskCard } from "@/components/task/task-card";
import { AttachmentPicker } from "@/components/task/attachment-picker";
import { toUploadItems, validateAttachmentFiles, type UploadItem } from "@/components/task/attachment-utils";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";

export default function AdminTasksPage() {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [planId, setPlanId] = useState("");
  const [userId, setUserId] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [dueAt, setDueAt] = useState("");
  const [error, setError] = useState<string>();
  const [pickerError, setPickerError] = useState<string>();
  const [uploads, setUploads] = useState<UploadItem[]>([]);
  const [createdTaskId, setCreatedTaskId] = useState<string>();
  const [submitting, setSubmitting] = useState(false);
  const query = useQuery({ queryKey: ["tasks", "all"], queryFn: () => taskService.listAll() });
  const summary = useQuery({ queryKey: ["tasks", "summary"], queryFn: () => taskService.summary() });
  const options = useQuery({ queryKey: ["tasks", "options"], queryFn: () => taskService.options() });

  if (query.isLoading || summary.isLoading || options.isLoading) return <LoadingSkeleton/>;
  if (query.isError || summary.isError || options.isError || !options.data) return <ErrorState retry={() => { query.refetch(); summary.refetch(); options.refetch(); }}/>;

  function addFiles(files: File[]) {
    setPickerError(undefined);
    const validation = validateAttachmentFiles(uploads.map((item) => item.file), files);
    if (validation) { setPickerError(validation); return; }
    setUploads((values) => [...values, ...toUploadItems(files)]);
  }

  async function uploadOne(taskId: string, item: UploadItem) {
    setUploads((values) => values.map((value) => value.id === item.id ? { ...value, status: "UPLOADING", error: undefined } : value));
    try {
      await taskService.uploadAttachment(taskId, item.file);
      setUploads((values) => values.map((value) => value.id === item.id ? { ...value, status: "SUCCESS" } : value));
      return true;
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Không thể tải tệp lên.";
      setUploads((values) => values.map((value) => value.id === item.id ? { ...value, status: "ERROR", error: message } : value));
      return false;
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    setSubmitting(true);
    try {
      const task = await taskService.create({ weeklyPlanId: planId, assigneeUserId: userId, title, description: description || undefined, dueAt: new Date(dueAt).toISOString() });
      setCreatedTaskId(task.id);
      if (uploads.length > 0) await Promise.all(uploads.map((item) => uploadOne(task.id, item)));
      await client.invalidateQueries({ queryKey: ["tasks"] });
      if (uploads.length === 0) closeAndReset();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Không thể giao nhiệm vụ.");
    } finally { setSubmitting(false); }
  }

  function closeAndReset() {
    setOpen(false); setPlanId(""); setUserId(""); setTitle(""); setDescription(""); setDueAt("");
    setError(undefined); setPickerError(undefined); setUploads([]); setCreatedTaskId(undefined); setSubmitting(false);
  }

  function openDialog() { closeAndReset(); setOpen(true); }
  const counts = summary.data!;
  const data = query.data ?? [];
  const successes = uploads.filter((item) => item.status === "SUCCESS").length;
  const failures = uploads.filter((item) => item.status === "ERROR").length;

  return <>
    <PageHeader eyebrow="Phân công cá nhân" title="Nhiệm vụ" description="Giao nhiệm vụ, tài liệu đính kèm và theo dõi trạng thái do máy chủ tính."
      actions={<Button onClick={openDialog}><ClipboardPlus size={17}/>Giao nhiệm vụ</Button>}/>
    <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">{Object.entries({ "Tổng nhiệm vụ": counts.total, "Đã hoàn thành": counts.completed, "Chưa hoàn thành": counts.incomplete, "Quá hạn": counts.overdue }).map(([label, value]) => <div className="card p-4" key={label}><strong className="text-2xl">{value}</strong><p className="text-xs text-slate-500">{label}</p></div>)}</div>
    {data.length ? <div className="grid gap-4 lg:grid-cols-2">{data.map((task) => <TaskCard key={task.id} task={task} admin/>)}</div> : <EmptyState title="Chưa có nhiệm vụ" description="Hãy giao nhiệm vụ đầu tiên."/>}
    <Dialog open={open} onOpenChange={(value) => { if (!value && !submitting) closeAndReset(); }} title="Giao nhiệm vụ" description="Nhiệm vụ được tạo trước; từng tệp sẽ tải riêng để có thể thử lại an toàn.">
      {!createdTaskId ? <form className="grid gap-4" onSubmit={submit}>
        <label><span className="field-label">Kế hoạch tuần *</span><select required className="field" value={planId} onChange={(event) => setPlanId(event.target.value)}><option value="">Chọn kế hoạch</option>{options.data.plans.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label><span className="field-label">Người nhận *</span><select required className="field" value={userId} onChange={(event) => setUserId(event.target.value)}><option value="">Chọn người nhận</option>{options.data.users.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label><span className="field-label">Tiêu đề *</span><input required maxLength={255} className="field" value={title} onChange={(event) => setTitle(event.target.value)}/></label>
        <label><span className="field-label">Mô tả</span><textarea className="field min-h-20" value={description} onChange={(event) => setDescription(event.target.value)}/></label>
        <label><span className="field-label">Hạn hoàn thành *</span><input required type="datetime-local" className="field" value={dueAt} onChange={(event) => setDueAt(event.target.value)}/></label>
        <AttachmentPicker items={uploads} error={pickerError} disabled={submitting} onFiles={addFiles}
          onRemove={(id) => setUploads((values) => values.filter((item) => item.id !== id))}/>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <div className="dialog-actions"><Button type="button" variant="secondary" disabled={submitting} onClick={closeAndReset}>Hủy</Button><Button type="submit" disabled={submitting}>{submitting ? "Đang tạo…" : "Giao nhiệm vụ"}</Button></div>
      </form> : <div>
        <p className="rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">Nhiệm vụ đã được tạo.</p>
        {uploads.length > 0 && <p className="mt-3 text-sm text-slate-700">{successes}/{uploads.length} tệp tải lên thành công.{failures > 0 ? ` ${failures} tệp thất bại.` : ""}</p>}
        <div className="mt-3"><AttachmentPicker items={uploads} disabled onFiles={() => undefined} onRemove={() => undefined}
          onRetry={(id) => { const item = uploads.find((value) => value.id === id); if (item) void uploadOne(createdTaskId, item); }}/></div>
        <div className="dialog-actions mt-5"><Button type="button" onClick={closeAndReset}>Đóng</Button></div>
      </div>}
    </Dialog>
  </>;
}
