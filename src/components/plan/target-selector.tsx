"use client";

import { Plus, X } from "lucide-react";
import { useState } from "react";
import type { PlanTarget, TargetType } from "@/types/domain";
import type { WeeklyPlanOptions } from "@/services";
import { Button } from "@/components/ui/button";

export function TargetSelector({ value, options, onChange }: {
  value: PlanTarget[]; options: WeeklyPlanOptions; onChange: (value: PlanTarget[]) => void;
}) {
  const [type, setType] = useState<TargetType>("ROLE");
  const [targetId, setTargetId] = useState("");
  const candidates = type === "ROLE" ? options.businessRoles : type === "DEPARTMENT"
    ? options.departments : type === "USER" ? options.users : [];

  function add() {
    if (type === "ALL") { onChange([{ type: "ALL", label: "Tất cả" }]); return; }
    const candidate = candidates.find((item) => item.id === targetId) ?? candidates[0];
    if (!candidate || value.some((item) => item.type === type && item.id === candidate.id)) return;
    onChange([...value.filter((item) => item.type !== "ALL"), { type, id: candidate.id, label: candidate.name }]);
    setTargetId("");
  }

  return <div><span className="field-label">Đối tượng</span><div className="mb-2 flex flex-wrap gap-2"><select className="field max-w-40" value={type} onChange={(event) => { setType(event.target.value as TargetType); setTargetId(""); }}><option value="ALL">Tất cả</option><option value="ROLE">Vai trò</option><option value="DEPARTMENT">Phòng ban</option><option value="USER">Người dùng</option></select>{type !== "ALL" && <select className="field min-w-48 flex-1" value={targetId} onChange={(event) => setTargetId(event.target.value)}><option value="">Chọn đối tượng</option>{candidates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>}<Button type="button" variant="secondary" size="sm" onClick={add}><Plus size={14}/>Thêm</Button></div><div className="flex flex-wrap gap-2">{value.map((target, index) => <span key={`${target.type}-${target.id ?? "all"}`} className="inline-flex items-center gap-1 rounded-full border bg-slate-50 px-3 py-1.5 text-xs font-semibold">{target.label}<button type="button" aria-label={`Xóa đối tượng ${target.label}`} onClick={() => onChange(value.filter((_, itemIndex) => itemIndex !== index))} className="rounded-full p-0.5 hover:bg-slate-200"><X size={12}/></button></span>)}</div></div>;
}
