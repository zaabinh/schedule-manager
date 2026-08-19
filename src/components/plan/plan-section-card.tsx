import { UsersRound } from "lucide-react";
import type { PlanSection } from "@/types/domain";
import { cn } from "@/lib/utils";

export function PlanSectionCard({ section }: { section: PlanSection }) {
  return <article className={cn("card p-3", section.relevant && "border-emerald-300 bg-emerald-50/35")}>
    <div className="flex items-center justify-between gap-2">
      <h3 className="min-w-0 text-[13px] font-bold leading-4 text-slate-900">{section.title}</h3>
      {section.relevant && <span title="Liên quan đến bạn" className="shrink-0 rounded-md bg-white p-1 text-[var(--primary)]"><UsersRound size={13}/></span>}
    </div>
    <p className="mt-1.5 text-xs leading-[1.15rem] text-slate-700">{section.content}</p>
    <div className="mt-2 flex flex-wrap gap-1">{section.targets.map((target) => <span key={`${target.type}-${target.label}`} className="rounded-full border bg-white px-1.5 py-0.5 text-[9px] font-semibold text-slate-600">{target.label}</span>)}</div>
  </article>;
}
