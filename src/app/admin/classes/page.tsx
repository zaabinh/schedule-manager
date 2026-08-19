"use client";

import { Edit3, Plus, Power } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { classService } from "@/services";
import type { SchoolClass } from "@/types/domain";
import { PageHeader } from "@/components/layout/page-header";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/ui/status-badge";

export default function ClassesPage() {
  const query = useQuery({ queryKey: ["classes-management"], queryFn: async () => ({
    classes: await classService.list(), options: await classService.options(),
  }) });
  const [editing, setEditing] = useState<SchoolClass | null>();
  const [yearId, setYearId] = useState("");
  const [name, setName] = useState("");
  const [grade, setGrade] = useState<10 | 11 | 12>(10);
  const [teacherId, setTeacherId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  if (query.isLoading) return <LoadingSkeleton/>;
  if (query.isError || !query.data) return <ErrorState retry={() => query.refetch()}/>;
  const { classes, options } = query.data;

  function open(value?: SchoolClass) {
    setEditing(value ?? null);
    setYearId(value?.academicYearId ?? options.academicYears[0]?.id ?? "");
    setName(value?.name ?? "");
    setGrade(value?.grade ?? 10);
    setTeacherId(value?.homeroomTeacherId ?? "");
    setError(undefined);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(undefined);
    const input = { academicYearId: yearId, name, grade, homeroomTeacherId: teacherId || undefined };
    try {
      if (editing) await classService.update(editing.id, { ...input, isActive: editing.isActive, version: editing.version });
      else await classService.create(input);
      setEditing(undefined); await query.refetch();
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể lưu lớp học."); }
    finally { setBusy(false); }
  }

  async function toggle(value: SchoolClass) {
    setError(undefined);
    try {
      await classService.update(value.id, { academicYearId: value.academicYearId, name: value.name, grade: value.grade,
        homeroomTeacherId: value.homeroomTeacherId ?? undefined, isActive: !value.isActive, version: value.version });
      await query.refetch();
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể thay đổi trạng thái lớp."); }
  }

  const teachers = [...options.availableTeachers];
  if (editing?.homeroomTeacherId && !teachers.some((teacher) => teacher.id === editing.homeroomTeacherId))
    teachers.unshift({ id: editing.homeroomTeacherId, name: editing.homeroomTeacher ?? "GVCN hiện tại" });

  return <>
    <PageHeader eyebrow="Cơ cấu lớp theo năm học" title="Lớp học" description="Mỗi giáo viên chủ nhiệm tối đa một lớp đang hoạt động."/>
    <div className="mb-4 flex justify-end"><Button disabled={options.academicYears.length === 0} onClick={() => open()}><Plus size={16}/>Thêm lớp học</Button></div>
    {options.academicYears.length === 0 && <p className="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">Chưa có năm học hoạt động. Hãy tạo hoặc kích hoạt một năm tại mục Năm học.</p>}
    {error && editing === undefined && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <div className="table-wrap"><table className="data-table"><thead><tr><th>Lớp</th><th>Năm học · Khối</th><th>Giáo viên chủ nhiệm</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
      {classes.map((item) => <tr key={item.id}><td><strong>{item.name}</strong></td><td>{item.academicYearName} · Khối {item.grade}</td><td>{item.homeroomTeacher ?? "Chưa có GVCN"}</td><td><StatusBadge status={item.isActive ? "ACTIVE" : "INACTIVE"}/></td><td><div className="flex gap-2"><Button size="sm" variant="secondary" onClick={() => open(item)}><Edit3 size={14}/>Sửa</Button><Button size="sm" variant="ghost" onClick={() => void toggle(item)}><Power size={14}/>{item.isActive ? "Vô hiệu hóa" : "Kích hoạt"}</Button></div></td></tr>)}
      {classes.length === 0 && <tr><td colSpan={5} className="py-10 text-center text-sm text-slate-500">Chưa có lớp học.</td></tr>}
    </tbody></table></div>
    <Dialog open={editing !== undefined} onOpenChange={(value) => !value && setEditing(undefined)} title={`${editing ? "Sửa" : "Thêm"} lớp học`} description="Tên lớp là duy nhất trong một năm học; GVCN phải là giáo viên đang hoạt động.">
      <form className="space-y-4" onSubmit={submit}><label><span className="field-label">Năm học *</span><select className="field" required value={yearId} onChange={(event) => setYearId(event.target.value)}>{options.academicYears.map((year) => <option key={year.id} value={year.id}>{year.name}</option>)}</select></label><div className="grid gap-4 sm:grid-cols-2"><label><span className="field-label">Tên lớp *</span><input className="field" required maxLength={50} value={name} onChange={(event) => setName(event.target.value)}/></label><label><span className="field-label">Khối *</span><select className="field" value={grade} onChange={(event) => setGrade(Number(event.target.value) as 10 | 11 | 12)}><option value={10}>10</option><option value={11}>11</option><option value={12}>12</option></select></label></div><label><span className="field-label">Giáo viên chủ nhiệm</span><select className="field" value={teacherId} onChange={(event) => setTeacherId(event.target.value)}><option value="">Chưa phân công</option>{teachers.map((teacher) => <option key={teacher.id} value={teacher.id}>{teacher.name}</option>)}</select></label>{error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}<div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setEditing(undefined)}>Hủy</Button><Button type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</Button></div></form>
    </Dialog>
  </>;
}
