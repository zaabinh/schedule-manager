import { AppShell } from "@/components/layout/app-shell";
import { AuthRouteGuard } from "@/components/auth/auth-route-guard";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AuthRouteGuard requiredRole="ADMIN"><AppShell admin>{children}</AppShell></AuthRouteGuard>;
}
