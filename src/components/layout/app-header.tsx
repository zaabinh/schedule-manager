"use client";

import { ChevronDown, Menu } from "lucide-react";
import * as Dropdown from "@radix-ui/react-dropdown-menu";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { NotificationPopover } from "@/components/notification/notification-popover";
import { authService } from "@/services";
import type { User } from "@/types/domain";

export function AppHeader({ admin, user, onMenu }: { admin: boolean; user: User; onMenu: () => void }) {
  const router = useRouter();
  const initial = Array.from(user.name.trim())[0]?.toLocaleUpperCase("vi") ?? "?";
  async function logout() { await authService.logout(); router.replace("/login"); }
  return <header data-app-header className="sticky top-0 z-20 flex h-14 min-w-0 items-center border-b bg-white/95 px-4 backdrop-blur">
    <button onClick={onMenu} aria-label="Mở menu" className="mr-3 rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"><Menu size={20}/></button>
    <div className="min-w-0 flex-1"><p className="truncate text-[13px] font-bold text-slate-800">{admin ? "Không gian quản trị" : "Cổng thông tin giáo viên"}</p><p className="truncate text-[11px] text-slate-500">Tuần 12 · 09/11 – 15/11/2026</p></div>
    <div className="flex items-center gap-1.5"><NotificationPopover admin={admin}/><Dropdown.Root><Dropdown.Trigger aria-label="Mở menu tài khoản" className="flex items-center gap-1.5 rounded-lg p-1 hover:bg-slate-100"><span className="grid h-7 w-7 place-items-center rounded-full bg-[var(--primary-soft)] text-xs font-bold text-[var(--primary)]">{initial}</span><ChevronDown className="hidden text-slate-400 sm:block" size={14}/></Dropdown.Trigger><Dropdown.Portal><Dropdown.Content align="end" sideOffset={8} className="z-50 min-w-48 rounded-xl border bg-white p-1 shadow-xl"><Dropdown.Label className="px-3 py-2 text-xs text-slate-500">{user.name}</Dropdown.Label><Dropdown.Separator className="my-1 h-px bg-slate-100"/>{admin && <Dropdown.Item asChild><Link className="block cursor-pointer rounded-lg px-3 py-2 text-sm outline-none hover:bg-slate-100" href="/dashboard">Chuyển sang giao diện giáo viên</Link></Dropdown.Item>}<Dropdown.Item onSelect={() => void logout()} className="block cursor-pointer rounded-lg px-3 py-2 text-sm outline-none hover:bg-slate-100">Đăng xuất</Dropdown.Item></Dropdown.Content></Dropdown.Portal></Dropdown.Root></div>
  </header>;
}
