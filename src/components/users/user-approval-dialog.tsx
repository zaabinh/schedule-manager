"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Building2, GraduationCap, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { SearchableSelect } from "@/components/ui/searchable-select";
import type { ApprovalOptions } from "@/services";
import type { User } from "@/types/domain";

const schema = z.object({
  departmentId: z.string().min(1, "Vui lòng chọn phòng ban"),
  businessRoleIds: z.array(z.string()).min(1, "Chọn ít nhất một vai trò"),
  homeroomClassId: z.string().optional(),
});
export type ApprovalValues = z.infer<typeof schema>;

export function UserApprovalDialog({ user, options, open, onOpenChange, onApprove }: {
  user?: User;
  options?: ApprovalOptions;
  open: boolean;
  onOpenChange: (value: boolean) => void;
  onApprove: (value: ApprovalValues) => Promise<void>;
}) {
  const [serverError, setServerError] = useState<string>();
  const { control, register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<ApprovalValues>({
    resolver: zodResolver(schema),
    defaultValues: { departmentId: "", businessRoleIds: [], homeroomClassId: "" },
  });

  if (!user) return null;
  function changeOpen(next: boolean) {
    if (!next) {
      reset({ departmentId: "", businessRoleIds: [], homeroomClassId: "" });
      setServerError(undefined);
    }
    onOpenChange(next);
  }
  async function submit(value: ApprovalValues) {
    setServerError(undefined);
    try { await onApprove(value); changeOpen(false); }
    catch (error) { setServerError(error instanceof Error ? error.message : "Không thể phê duyệt tài khoản."); }
  }

  return <Dialog open={open} onOpenChange={changeOpen} title="Xem và phê duyệt tài khoản" description="Cấu hình thông tin nghiệp vụ trước khi kích hoạt tài khoản.">
    <div className="mb-5 flex items-center gap-3 rounded-xl border border-emerald-900/10 bg-[var(--primary-soft)] p-4">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-white font-bold text-[var(--primary)] shadow-sm">{user.name.slice(0, 1).toUpperCase()}</span>
      <div className="min-w-0"><p className="truncate font-bold">{user.name}</p><p className="truncate text-sm text-slate-600">{user.email}</p></div>
    </div>
    <form className="space-y-5" onSubmit={handleSubmit(submit)}>
      <div>
        <span className="field-label flex items-center gap-2"><Building2 size={15}/>Phòng ban *</span>
        <Controller name="departmentId" control={control} render={({ field }) => <SearchableSelect
          ariaLabel="Phòng ban"
          value={field.value}
          onValueChange={field.onChange}
          options={(options?.departments ?? []).map((item) => ({ value: item.id, label: item.name }))}
          placeholder="Chọn phòng ban"
          searchPlaceholder="Tìm phòng ban…"
          disabled={!options}
        />}/>
        {errors.departmentId && <span className="mt-1 block text-xs text-red-600">{errors.departmentId.message}</span>}
      </div>
      <fieldset>
        <legend className="field-label flex items-center gap-2"><ShieldCheck size={15}/>Vai trò *</legend>
        <div className="grid gap-2 sm:grid-cols-2">{options?.businessRoles.map((role) => <label key={role.id} className="flex cursor-pointer items-center gap-3 rounded-xl border bg-white p-3 text-sm transition hover:border-emerald-900/25 hover:bg-slate-50"><input className="h-4 w-4 accent-[var(--primary)]" type="checkbox" value={role.id} {...register("businessRoleIds")}/><span className="font-medium">{role.name}</span></label>)}</div>
        {errors.businessRoleIds && <span className="mt-1 block text-xs text-red-600">{errors.businessRoleIds.message}</span>}
      </fieldset>
      <div>
        <span className="field-label flex items-center gap-2"><GraduationCap size={15}/>Lớp chủ nhiệm</span>
        <Controller name="homeroomClassId" control={control} render={({ field }) => <SearchableSelect
          ariaLabel="Lớp chủ nhiệm"
          value={field.value ?? ""}
          onValueChange={field.onChange}
          options={(options?.classes ?? []).map((item) => ({ value: item.id, label: item.name }))}
          placeholder="Chưa phân công"
          searchPlaceholder="Tìm lớp học…"
          clearable
          disabled={!options}
        />}/>
      </div>
      {serverError && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{serverError}</p>}
      <div className="dialog-actions"><Button type="button" variant="secondary" onClick={() => changeOpen(false)}>Hủy</Button><Button type="submit" disabled={isSubmitting || !options}>{isSubmitting ? "Đang phê duyệt…" : "Phê duyệt"}</Button></div>
    </form>
  </Dialog>;
}
