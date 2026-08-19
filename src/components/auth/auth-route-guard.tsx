"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { authService } from "@/services";
import { ApiClientError } from "@/services/http";
import type { User } from "@/types/domain";

type SystemRole = User["systemRole"];
const AuthUserContext = createContext<User | null>(null);

export function useAuthUser() {
  const user = useContext(AuthUserContext);
  if (!user) throw new Error("useAuthUser must be used inside AuthRouteGuard");
  return user;
}

function dashboardFor(role: SystemRole) {
  return role === "ADMIN" ? "/admin/dashboard" : "/dashboard";
}

export function AuthRouteGuard({ requiredRole, children }: { requiredRole: SystemRole; children: ReactNode }) {
  const router = useRouter();
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<"checking" | "allowed" | "failed">("checking");
  const [currentUser, setCurrentUser] = useState<User>();

  useEffect(() => {
    let active = true;
    authService.getCurrentUser().then((user) => {
      if (!active) return;
      if (user.systemRole !== requiredRole) {
        router.replace(dashboardFor(user.systemRole));
        return;
      }
      setCurrentUser(user);
      setState("allowed");
    }).catch((error: unknown) => {
      if (!active) return;
      if (error instanceof ApiClientError && error.status === 401) {
        router.replace("/login");
        return;
      }
      setState("failed");
    });
    return () => { active = false; };
  }, [attempt, requiredRole, router]);

  if (state === "allowed" && currentUser) return <AuthUserContext.Provider value={currentUser}>{children}</AuthUserContext.Provider>;
  return <AuthCheckState failed={state === "failed"} retry={() => {
    setState("checking");
    setAttempt((value) => value + 1);
  }}/>;
}

export function AuthLandingRedirect() {
  const router = useRouter();
  const [attempt, setAttempt] = useState(0);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    authService.getCurrentUser()
      .then((user) => { if (active) router.replace(dashboardFor(user.systemRole)); })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiClientError && error.status === 401) router.replace("/login");
        else setFailed(true);
      });
    return () => { active = false; };
  }, [attempt, router]);

  return <AuthCheckState failed={failed} retry={() => {
    setFailed(false);
    setAttempt((value) => value + 1);
  }}/>;
}

function AuthCheckState({ failed, retry }: { failed: boolean; retry: () => void }) {
  return <main className="grid min-h-screen place-items-center bg-slate-50 p-6">
    <div className="text-center" role="status" aria-live="polite">
      {failed ? <>
        <p className="font-semibold text-slate-800">Không thể kiểm tra phiên đăng nhập</p>
        <p className="mt-1 text-sm text-slate-500">Hãy kiểm tra backend và thử lại.</p>
        <button type="button" onClick={retry} className="mt-4 rounded-lg bg-[var(--primary)] px-4 py-2 text-sm font-semibold text-white">Thử lại</button>
      </> : <>
        <span className="mx-auto block h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-[var(--primary)]"/>
        <p className="mt-3 text-sm text-slate-500">Đang kiểm tra phiên đăng nhập…</p>
      </>}
    </div>
  </main>;
}
