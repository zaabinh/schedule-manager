import { CheckCircle2, CircleDashed, Clock3, ShieldCheck, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

const labels: Record<string, string> = { DRAFT: "Bản nháp", PUBLISHED: "Đã công bố", PENDING: "Chờ duyệt", ACTIVE: "Đang hoạt động", INACTIVE: "Ngừng hoạt động", TODO: "Chưa hoàn thành", COMPLETED: "Đã hoàn thành", OVERDUE: "Quá hạn", OPEN: "Đang mở", CLOSED: "Đã đóng" };
export function StatusBadge({ status, className }: { status: string; className?: string }) {
  const positive = ["PUBLISHED", "ACTIVE", "COMPLETED"].includes(status);
  const warning = ["DRAFT", "PENDING", "TODO", "OPEN"].includes(status);
  const danger = status === "OVERDUE";
  const Icon = positive ? CheckCircle2 : danger ? Clock3 : status === "INACTIVE" || status === "CLOSED" ? XCircle : status === "PUBLISHED" ? ShieldCheck : CircleDashed;
  return <span className={cn("inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold", positive && "border-emerald-200 bg-emerald-50 text-emerald-800", warning && "border-amber-200 bg-amber-50 text-amber-800", danger && "border-red-200 bg-red-50 text-red-700", !positive && !warning && !danger && "bg-slate-100 text-slate-600", className)}><Icon size={13} aria-hidden />{labels[status] ?? status}</span>;
}
