"use client";

import { Activity, Bell, BriefcaseBusiness, Building2, CalendarDays, CalendarRange, ClipboardCheck, FileClock, Home, IdCard, MessageSquareText, School, UserRound, UsersRound, X } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { SCHOOL_LOGO_PATH, SCHOOL_NAME } from "@/lib/brand";
import type { User } from "@/types/domain";

const userItems = [
  ["Tổng quan", "/dashboard", Home], ["Kế hoạch tuần", "/weekly-plan", CalendarDays], ["Phân công của tôi", "/assignments", ClipboardCheck],
  ["Nhắc lịch", "/reminders", Bell], ["Trao đổi", "/conversations", MessageSquareText], ["Thông báo", "/notifications", Activity], ["Hồ sơ", "/profile", UserRound],
] as const;
const adminItems = [
  ["Tổng quan", "/admin/dashboard", Home], ["Kế hoạch tuần", "/admin/weekly-plans", CalendarDays], ["Người dùng", "/admin/users", UsersRound],
  ["Năm học", "/admin/academic-years", CalendarRange], ["Lớp học", "/admin/classes", School], ["Phòng ban", "/admin/departments", Building2], ["Vai trò", "/admin/roles", IdCard],
  ["Phân công", "/admin/tasks", BriefcaseBusiness], ["Trao đổi", "/admin/conversations", MessageSquareText], ["Thông báo", "/admin/notifications", Bell], ["Audit Log", "/admin/audit", FileClock],
] as const;

export function AppSidebar({ admin, user, mobile = false, onClose }: { admin: boolean; user: User; mobile?: boolean; onClose?: () => void }) {
  const pathname = usePathname(); const items = admin ? adminItems : userItems;
  const initial = Array.from(user.name.trim())[0]?.toLocaleUpperCase("vi") ?? "?";
  const context = [user.department, ...user.businessRoles].filter(Boolean).join(" · ") || (admin ? "Quản trị hệ thống" : "Giáo viên");
  return <aside data-app-sidebar className={cn("flex h-full w-60 shrink-0 flex-col bg-[var(--sidebar)] text-white lg:w-52", !mobile && "fixed inset-y-0 left-0 z-30 hidden lg:flex")}>
    <div className="flex min-h-16 items-center gap-2.5 border-b border-white/10 px-3 py-2"><Image src={SCHOOL_LOGO_PATH} alt={`Logo ${SCHOOL_NAME}`} width={36} height={36} className="h-9 w-9 shrink-0 rounded-lg bg-white object-contain p-0.5"/><div className="min-w-0"><p className="text-[9px] font-semibold leading-tight text-emerald-100/80">{SCHOOL_NAME}</p><p className="mt-0.5 text-xs font-bold">CTTTDQLKH</p></div>{mobile && <button onClick={onClose} aria-label="Đóng menu" className="ml-auto rounded-lg p-2 hover:bg-white/10"><X /></button>}</div>
    <nav aria-label={admin ? "Điều hướng quản trị" : "Điều hướng người dùng"} className="flex-1 space-y-0.5 overflow-y-auto p-2">{items.map(([label, href, Icon]) => { const active = pathname === href || (href !== "/admin/dashboard" && href !== "/dashboard" && pathname.startsWith(href)); return <Link onClick={onClose} key={href} href={href} className={cn("flex items-center gap-2.5 rounded-md px-2.5 py-2 text-[13px] font-medium text-emerald-50/80 hover:bg-white/10 hover:text-white", active && "bg-white text-[var(--sidebar)] hover:bg-white hover:text-[var(--sidebar)]")}><Icon size={16} aria-hidden />{label}</Link>; })}</nav>
    <div className="border-t border-white/10 p-3"><div className="flex items-center gap-2.5"><span className="grid h-8 w-8 place-items-center rounded-full bg-emerald-100 text-sm font-bold text-[var(--sidebar)]">{initial}</span><div className="min-w-0"><p className="truncate text-xs font-semibold">{user.name}</p><p className="truncate text-[10px] text-emerald-100/60">{context}</p></div></div></div>
  </aside>;
}
