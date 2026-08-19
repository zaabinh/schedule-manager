"use client";

import { BriefcaseBusiness, Building2, GraduationCap, Mail, UserRound } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { authService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { ErrorState, LoadingSkeleton } from "@/components/ui/states";

export default function ProfilePage() {
  const query=useQuery({queryKey:["auth","me"],queryFn:()=>authService.getCurrentUser()});
  if(query.isLoading)return <LoadingSkeleton/>;
  if(query.isError||!query.data)return <ErrorState retry={()=>query.refetch()}/>;
  const user=query.data;
  return <><PageHeader eyebrow="Tài khoản" title="Hồ sơ của tôi" description="Thông tin nghiệp vụ do nhà trường cấu hình."/><div className="card max-w-3xl p-6"><div className="flex items-center gap-4 border-b pb-6"><span className="grid h-16 w-16 place-items-center rounded-full bg-[var(--primary-soft)] text-2xl font-bold text-[var(--primary)]">{user.name.charAt(0).toUpperCase()}</span><div><h2 className="text-xl font-bold">{user.name}</h2><p className="mt-1 text-sm text-slate-500">Tài khoản đang hoạt động</p></div></div><dl data-profile-details className="mt-6 grid gap-5 sm:grid-cols-2"><Info icon={<Mail/>} label="Email" value={user.email}/><Info icon={<Building2/>} label="Phòng ban" value={user.department??"Chưa cấu hình"}/><Info icon={<BriefcaseBusiness/>} label="Vai trò nghiệp vụ" value={user.businessRoles.join(", ")||"Không có"}/><Info icon={<GraduationCap/>} label="Lớp chủ nhiệm" value={user.homeroomClass??"Không có"}/><Info icon={<UserRound/>} label="System Role" value="Người dùng"/></dl></div></>;
}

function Info({icon,label,value}:{icon:React.ReactElement;label:string;value:string}){return <div className="relative pl-9"><dt className="text-xs font-semibold text-slate-500"><span aria-hidden="true" className="absolute left-0 top-0 text-slate-400">{icon}</span>{label}</dt><dd className="mt-1 font-medium">{value}</dd></div>}
