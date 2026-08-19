import { AppShell } from "@/components/layout/app-shell";
import { AuthRouteGuard } from "@/components/auth/auth-route-guard";

export default function UserLayout({ children }: { children: React.ReactNode }) {
  return <AuthRouteGuard requiredRole="USER"><AppShell admin={false}>{children}</AppShell></AuthRouteGuard>;
}
