"use client";

import { Plus, X } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { SearchableSelect } from "@/components/ui/searchable-select";
import type { WeeklyPlanOptions } from "@/services";
import type { PlanTarget, TargetType } from "@/types/domain";

export function TargetSelector({ value, options, onChange }: {
  value: PlanTarget[];
  options: WeeklyPlanOptions;
  onChange: (value: PlanTarget[]) => void;
}) {
  const [type, setType] = useState<TargetType>("ROLE");
  const [targetId, setTargetId] = useState("");
  const candidates = type === "ROLE" ? options.businessRoles : type === "DEPARTMENT"
    ? options.departments : type === "USER" ? options.users : [];

  function add() {
    if (type === "ALL") { onChange([{ type: "ALL", label: "Tất cả" }]); return; }
    const candidate = candidates.find((item) => item.id === targetId);
    if (!candidate || value.some((item) => item.type === type && item.id === candidate.id)) return;
    onChange([...value.filter((item) => item.type !== "ALL"), { type, id: candidate.id, label: candidate.name }]);
    setTargetId("");
  }

  return <div>
    <span className="field-label">Đối tượng</span>
    <div className="grid gap-2 sm:grid-cols-[150px_minmax(0,1fr)_auto]">
      <select className="field" aria-label="Loại đối tượng" value={type} onChange={(event) => { setType(event.target.value as TargetType); setTargetId(""); }}>
        <option value="ALL">Tất cả</option><option value="ROLE">Vai trò</option><option value="DEPARTMENT">Phòng ban</option><option value="USER">Người dùng</option>
      </select>
      {type !== "ALL" ? <SearchableSelect
        ariaLabel="Đối tượng cụ thể"
        value={targetId}
        onValueChange={setTargetId}
        placeholder="Chọn đối tượng"
        searchPlaceholder="Nhập tên để tìm…"
        options={candidates.map((item) => ({ value: item.id, label: item.name }))}
      /> : <div className="flex min-h-11 items-center rounded-lg bg-slate-50 px-3 text-sm text-slate-500">Áp dụng cho toàn trường</div>}
      <Button className="min-h-11" type="button" variant="secondary" size="sm" disabled={type !== "ALL" && !targetId} onClick={add}><Plus size={14}/>Thêm</Button>
    </div>
    {value.length > 0 && <div className="mt-3 flex flex-wrap gap-2">{value.map((target, index) => <span key={`${target.type}-${target.id ?? "all"}`} className="inline-flex items-center gap-1.5 rounded-full border border-emerald-900/10 bg-[var(--primary-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--primary-strong)]">{target.label}<button type="button" aria-label={`Xóa đối tượng ${target.label}`} onClick={() => onChange(value.filter((_, itemIndex) => itemIndex !== index))} className="rounded-full p-0.5 hover:bg-emerald-900/10"><X size={12}/></button></span>)}</div>}
  </div>;
}
