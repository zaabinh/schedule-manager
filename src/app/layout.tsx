import type { Metadata } from "next";
import { connection } from "next/server";
import "./globals.css";
import { QueryProvider } from "@/lib/query-provider";
import { SCHOOL_LOGO_PATH, SCHOOL_NAME } from "@/lib/brand";

export const metadata: Metadata = {
  title: `Cổng thông tin theo dõi và quản lí kế hoạch`,
  description: `Hệ thống quản lý kế hoạch của ${SCHOOL_NAME}`,
  icons: { icon: SCHOOL_LOGO_PATH, apple: SCHOOL_LOGO_PATH },
};

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  await connection();
  return <html lang="vi"><body><QueryProvider>{children}</QueryProvider></body></html>;
}
