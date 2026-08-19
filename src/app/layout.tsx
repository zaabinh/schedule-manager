import type { Metadata } from "next";
import { connection } from "next/server";
import "./globals.css";
import { QueryProvider } from "@/lib/query-provider";

export const metadata: Metadata = { title: "Kế hoạch tuần · THPT", description: "Hệ thống quản lý kế hoạch tuần trường học" };

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  await connection();
  return <html lang="vi"><body><QueryProvider>{children}</QueryProvider></body></html>;
}
