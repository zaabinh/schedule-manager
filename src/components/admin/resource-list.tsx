"use client";

import { Edit3, Plus, Power } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/ui/status-badge";

export interface ResourceRow {
  id: string; name: string; description?: string; detail?: string; isActive: boolean;
  protected?: boolean; version: number;
}

interface ResourceListProps {
  title: string;
  rows: ResourceRow[];
  onCreate: (input: { name: string; description?: string }) => Promise<void>;
  onUpdate: (row: ResourceRow, input: { name: string; description?: string; isActive: boolean; version: number }) => Promise<void>;
}

export function ResourceList({ title, rows, onCreate, onUpdate }: ResourceListProps) {
  const [editing, setEditing] = useState<ResourceRow | null>();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  function open(row?: ResourceRow) {
    setEditing(row ?? null);
    setName(row?.name ?? "");
    setDescription(row?.description ?? "");
    setError(undefined);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true); setError(undefined);
    try {
      if (editing) await onUpdate(editing, { name, description: description || undefined, isActive: editing.isActive, version: editing.version });
      else await onCreate({ name, description: description || undefined });
      setEditing(undefined);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Không thể lưu dữ liệu.");
    } finally { setBusy(false); }
  }

  async function toggle(row: ResourceRow) {
    setError(undefined);
    try { await onUpdate(row, { name: row.name, description: row.description, isActive: !row.isActive, version: row.version }); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể thay đổi trạng thái."); }
  }

  return <>
    <div className="mb-4 flex justify-end"><Button onClick={() => open()}><Plus size={16}/>Thêm {title.toLowerCase()}</Button></div>
    {error && editing === undefined && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên</th><th>Mô tả</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
      {rows.map((row) => <tr key={row.id}><td><strong>{row.name}</strong>{row.protected && <span className="ml-2 rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold uppercase text-slate-500">Mặc định</span>}</td><td>{row.description ?? row.detail ?? "—"}</td><td><StatusBadge status={row.isActive ? "ACTIVE" : "INACTIVE"}/></td><td><div className="flex gap-2"><Button variant="secondary" size="sm" disabled={row.protected} onClick={() => open(row)}><Edit3 size={14}/>Sửa</Button><Button variant="ghost" size="sm" disabled={row.protected} title={row.protected ? "Vai trò mặc định được bảo vệ" : undefined} onClick={() => void toggle(row)}><Power size={14}/>{row.isActive ? "Vô hiệu hóa" : "Kích hoạt"}</Button></div></td></tr>)}
      {rows.length === 0 && <tr><td colSpan={4} className="py-10 text-center text-sm text-slate-500">Chưa có dữ liệu.</td></tr>}
    </tbody></table></div>
    <Dialog open={editing !== undefined} onOpenChange={(value) => !value && setEditing(undefined)} title={`${editing ? "Sửa" : "Thêm"} ${title.toLowerCase()}`} description="Tên được kiểm tra trùng sau khi chuẩn hóa dấu và khoảng trắng.">
      <form className="space-y-4" onSubmit={submit}><label><span className="field-label">Tên *</span><input className="field" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)}/></label><label><span className="field-label">Mô tả</span><textarea className="field min-h-24" maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)}/></label>{error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}<div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setEditing(undefined)}>Hủy</Button><Button type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</Button></div></form>
    </Dialog>
  </>;
}
