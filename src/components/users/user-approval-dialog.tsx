"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { User } from "@/types/domain";
import type { ApprovalOptions } from "@/services";
import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

const schema = z.object({ departmentId: z.string().min(1, "Vui lòng chọn phòng ban"), businessRoleIds: z.array(z.string()).min(1, "Chọn ít nhất một vai trò"), homeroomClassId: z.string().optional() });
export type ApprovalValues = z.infer<typeof schema>;

export function UserApprovalDialog({ user, options, open, onOpenChange, onApprove }: {
  user?: User; options?: ApprovalOptions; open: boolean; onOpenChange: (value: boolean) => void;
  onApprove: (value: ApprovalValues) => Promise<void>;
}) {
  const [serverError, setServerError] = useState<string>();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ApprovalValues>({ resolver: zodResolver(schema), defaultValues: { departmentId: "", businessRoleIds: [], homeroomClassId: "" } });
  if (!user) return null;
  async function submit(value: ApprovalValues) {
    setServerError(undefined);
    try { await onApprove(value); onOpenChange(false); }
    catch (error) { setServerError(error instanceof Error ? error.message : "Không thể phê duyệt tài khoản."); }
  }
  return <Dialog open={open} onOpenChange={onOpenChange} title="Xem và phê duyệt tài khoản" description="Cấu hình thông tin nghiệp vụ trước khi kích hoạt tài khoản."><div className="mb-5 grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-2"><div><p className="text-xs text-slate-500">Họ tên</p><p className="font-semibold">{user.name}</p></div><div><p className="text-xs text-slate-500">Email</p><p className="font-semibold">{user.email}</p></div></div><form className="space-y-4" onSubmit={handleSubmit(submit)}><label><span className="field-label">Phòng ban *</span><select className="field" {...register("departmentId")}><option value="">Chọn phòng ban</option>{options?.departments.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{errors.departmentId && <span className="text-xs text-red-600">{errors.departmentId.message}</span>}</label><fieldset><legend className="field-label">Vai trò *</legend><div className="grid gap-2 sm:grid-cols-2">{options?.businessRoles.map((role) => <label key={role.id} className="flex items-center gap-2 rounded-lg border p-3 text-sm"><input type="checkbox" value={role.id} {...register("businessRoleIds")}/>{role.name}</label>)}</div>{errors.businessRoleIds && <span className="text-xs text-red-600">{errors.businessRoleIds.message}</span>}</fieldset><label><span className="field-label">Lớp chủ nhiệm</span><select className="field" {...register("homeroomClassId")}><option value="">Không có</option>{options?.classes.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>{serverError && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{serverError}</p>}<div className="flex justify-end gap-2 border-t pt-4"><Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>Hủy</Button><Button type="submit" disabled={isSubmitting || !options}>{isSubmitting ? "Đang phê duyệt…" : "Phê duyệt"}</Button></div></form></Dialog>;
}
