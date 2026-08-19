"use client";

import Link from "next/link";
import { useState } from "react";
import { CheckCircle2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { authService } from "@/services";

const passwordSchema = z.string()
  .min(8, "Mật khẩu cần ít nhất 8 ký tự")
  .regex(/\p{Ll}/u, "Mật khẩu phải có chữ thường")
  .regex(/\p{Lu}/u, "Mật khẩu phải có chữ hoa")
  .regex(/[^\p{L}\p{N}\s]/u, "Mật khẩu phải có ký tự đặc biệt")
  .refine((value) => new TextEncoder().encode(value).length <= 72, "Mật khẩu tối đa 72 byte UTF-8");

const schema = z.object({
  name: z.string().trim().min(2, "Vui lòng nhập họ tên").max(150),
  email: z.email("Email không hợp lệ").max(320),
  password: passwordSchema,
});
type Values = z.infer<typeof schema>;

export default function RegisterPage() {
  const [done, setDone] = useState(false);
  const [serverError, setServerError] = useState<string>();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema) });

  async function submit(value: Values) {
    setServerError(undefined);
    try {
      await authService.register({ email: value.email, password: value.password, displayName: value.name });
      setDone(true);
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "Không thể gửi đăng ký.");
    }
  }

  return <main className="grid min-h-screen place-items-center bg-slate-50 p-5"><div className="card w-full max-w-lg p-6 sm:p-8">{done ? <div className="py-8 text-center"><CheckCircle2 className="mx-auto text-emerald-600" size={48}/><h1 className="mt-4 text-2xl font-bold">Đăng ký thành công</h1><p className="mt-2 text-sm leading-6 text-slate-500">Tài khoản đang chờ Hiệu trưởng cấu hình phòng ban, vai trò và phê duyệt.</p><Button asChild className="mt-6"><Link href="/login">Quay lại đăng nhập</Link></Button></div> : <><p className="eyebrow">Tài khoản giáo viên</p><h1 className="mt-2 text-3xl font-bold">Đăng ký</h1><p className="mt-2 text-sm text-slate-500">Mật khẩu nên là một cụm từ dài, dễ nhớ và chưa từng sử dụng ở nơi khác.</p><form className="mt-7 grid gap-4" onSubmit={handleSubmit(submit)}><label className="block"><span className="field-label">Họ và tên *</span><input className="field" autoComplete="name" {...register("name")}/>{errors.name && <span className="mt-1 block text-xs text-red-600">{errors.name.message}</span>}</label><label className="block"><span className="field-label">Email *</span><input className="field" type="email" autoComplete="email" {...register("email")}/>{errors.email && <span className="mt-1 block text-xs text-red-600">{errors.email.message}</span>}</label><label className="block"><span className="field-label">Mật khẩu *</span><input className="field" type="password" autoComplete="new-password" {...register("password")}/>{errors.password && <span className="mt-1 block text-xs text-red-600">{errors.password.message}</span>}</label>{serverError && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{serverError}</p>}<Button className="w-full" type="submit" disabled={isSubmitting}>{isSubmitting ? "Đang gửi…" : "Gửi đăng ký"}</Button></form><p className="mt-5 text-center text-sm text-slate-500">Đã có tài khoản? <Link className="font-semibold text-[var(--primary)]" href="/login">Đăng nhập</Link></p></>}</div></main>;
}
