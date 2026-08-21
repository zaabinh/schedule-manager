"use client";

import { BookOpenCheck, Eye, LockKeyhole, Mail } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { authService } from "@/services";

const schema = z.object({
  email: z.email("Email không hợp lệ"),
  password: z.string().min(8, "Mật khẩu cần ít nhất 8 ký tự"),
});
type LoginData = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const [show, setShow] = useState(false);
  const [serverError, setServerError] = useState<string>();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginData>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });

  async function submit(data: LoginData) {
    setServerError(undefined);
    try {
      const user = await authService.login(data.email, data.password);
      router.push(user.systemRole === "ADMIN" ? "/admin/dashboard" : "/dashboard");
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "Không thể đăng nhập.");
    }
  }

  return <main className="grid min-h-screen lg:grid-cols-[1.05fr_.95fr]">
    <section className="hidden bg-[var(--sidebar)] p-12 text-white lg:flex lg:flex-col lg:justify-between">
      <div className="flex items-center gap-3"><span className="rounded-xl bg-white/10 p-3"><BookOpenCheck/></span><span className="font-bold">TRƯỜNG THPT SỐ 2 PHAN BỘI CHÂU GIA LAI</span></div>
      <div className="max-w-xl"><p className="eyebrow !text-emerald-200">Trường THPT số 2 Phan Bội Châu Gia Lai</p><h1 className="mt-4 text-5xl font-bold leading-tight">Cổng thông tin<br/>theo dõi kế hoạch dành cho giáo viên - cán bộ</h1><p className="mt-6 max-w-lg text-lg leading-8 text-emerald-50/70">Theo dõi kế hoạch tuần, phân công và nhắc lịch dễ dàng.</p></div>
      <p className="text-sm text-emerald-100/80">Hệ thống nội bộ · Dữ liệu được bảo vệ</p>
    </section>
    <section className="flex items-center justify-center bg-white p-6"><div className="w-full max-w-md">
      <div className="mb-9 lg:hidden"><span className="inline-flex rounded-xl bg-[var(--primary-soft)] p-3 text-[var(--primary)]"><BookOpenCheck/></span></div>
      <p className="eyebrow">Chào mừng trở lại</p><h2 className="mt-2 text-3xl font-bold">Đăng nhập</h2><p className="mt-2 text-sm text-slate-500">Sử dụng tài khoản đã được nhà trường phê duyệt.</p>
      <form className="mt-8 space-y-5" onSubmit={handleSubmit(submit)}>
        <label className="block"><span className="field-label">Email</span><span className="relative block"><Mail aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18}/><input className="field field-with-leading-icon" type="email" autoComplete="email" {...register("email")}/></span>{errors.email && <span className="mt-1 block text-xs text-red-600">{errors.email.message}</span>}</label>
        <div className="block"><label className="field-label" htmlFor="login-password">Mật khẩu</label><span className="relative block"><LockKeyhole aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18}/><input id="login-password" className="field field-with-both-icons" type={show ? "text" : "password"} autoComplete="current-password" {...register("password")}/><button type="button" aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"} onClick={() => setShow(!show)} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1.5 text-slate-400 hover:text-slate-600"><Eye size={18}/></button></span>{errors.password && <span className="mt-1 block text-xs text-red-600">{errors.password.message}</span>}</div>
        <div className="text-right"><button type="button" className="text-sm font-semibold text-[var(--primary)]">Quên mật khẩu?</button></div>
        {serverError && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{serverError}</p>}
        <Button className="w-full" size="lg" disabled={isSubmitting}>{isSubmitting ? "Đang đăng nhập…" : "Đăng nhập"}</Button>
      </form>
      <p className="mt-7 text-center text-sm text-slate-500">Chưa có tài khoản? <Link className="font-semibold text-[var(--primary)]" href="/register">Đăng ký</Link></p>
    </div></section>
  </main>;
}
