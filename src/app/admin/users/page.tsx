"use client";

import { Filter, UserCheck } from "lucide-react";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { userService } from "@/services";
import type { AccountStatus, User } from "@/types/domain";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
import { UserApprovalDialog } from "@/components/users/user-approval-dialog";
import { cn } from "@/lib/utils";

const filters: { label: string; value: "ALL" | AccountStatus }[] = [
  { label: "Tất cả", value: "ALL" }, { label: "Chờ duyệt", value: "PENDING" },
  { label: "Hoạt động", value: "ACTIVE" }, { label: "Ngừng hoạt động", value: "INACTIVE" },
];

export default function UsersPage() {
  const [filter, setFilter] = useState<(typeof filters)[number]["value"]>("ALL");
  const [selected, setSelected] = useState<User>();
  const client = useQueryClient();
  const query = useQuery({ queryKey: ["users", filter], queryFn: () => userService.list(filter === "ALL" ? undefined : filter) });
  const options = useQuery({ queryKey: ["user-approval-options"], queryFn: () => userService.approvalOptions() });
  if (query.isLoading) return <LoadingSkeleton/>;
  if (query.isError) return <ErrorState retry={() => query.refetch()}/>;
  const list = query.data ?? [];

  async function refresh() { await client.invalidateQueries({ queryKey: ["users"] }); }

  return <><PageHeader eyebrow="Tài khoản và phân quyền" title="Người dùng" description="Phê duyệt, cấu hình nghiệp vụ và thay đổi trạng thái tài khoản. Không xóa vật lý."/><div className="mb-5 flex gap-2 overflow-x-auto"><span className="grid place-items-center text-slate-400"><Filter size={18}/></span>{filters.map((item) => <button onClick={() => setFilter(item.value)} key={item.value} className={cn("whitespace-nowrap rounded-full border bg-white px-4 py-2 text-sm font-semibold", filter === item.value && "border-[var(--primary)] bg-[var(--primary)] text-white")}>{item.label}</button>)}</div><div className="table-wrap" tabIndex={0} aria-label="Danh sách người dùng"><table className="data-table"><thead><tr><th>Người dùng</th><th>Phòng ban</th><th>Vai trò</th><th>Lớp chủ nhiệm</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{list.map((user) => <tr key={user.id}><td><strong>{user.name}</strong><span className="block text-xs text-slate-500">{user.email}</span></td><td>{user.department ?? "—"}</td><td><div className="flex max-w-64 flex-wrap gap-1">{user.businessRoles.map((role) => <span className="rounded-full bg-slate-100 px-2 py-1 text-xs" key={role}>{role}</span>)}</div></td><td>{user.homeroomClass ?? "—"}</td><td><StatusBadge status={user.status}/></td><td>{user.status === "PENDING" ? <div className="flex gap-2"><Button size="sm" onClick={() => setSelected(user)}><UserCheck size={15}/>Xem & duyệt</Button><Button size="sm" variant="ghost" onClick={async () => { await userService.setStatus(user.id, "INACTIVE", user.version ?? 0); await refresh(); }}>Từ chối</Button></div> : user.systemRole === "USER" ? <div className="flex gap-2"><Button size="sm" variant="secondary">Chỉnh sửa</Button><Button size="sm" variant="ghost" onClick={async () => { await userService.setStatus(user.id, user.status === "ACTIVE" ? "INACTIVE" : "ACTIVE", user.version ?? 0); await refresh(); }}>{user.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}</Button></div> : <span className="text-xs text-slate-600">Được bảo vệ</span>}</td></tr>)}</tbody></table></div><UserApprovalDialog user={selected} options={options.data} open={Boolean(selected)} onOpenChange={(open) => !open && setSelected(undefined)} onApprove={async (value) => { if (!selected) return; await userService.approve(selected.id, { departmentId: value.departmentId, businessRoleIds: value.businessRoleIds, homeroomClassId: value.homeroomClassId || undefined, version: selected.version ?? 0 }); await refresh(); }}/></>;
}
