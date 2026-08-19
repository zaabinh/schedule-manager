"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { useState, type ReactNode } from "react";
import { AppHeader } from "./app-header";
import { AppSidebar } from "./app-sidebar";
import { useAuthUser } from "@/components/auth/auth-route-guard";

export function AppShell({ admin, children }: { admin: boolean; children: ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const user = useAuthUser();
  return <div data-app-shell className="min-h-screen min-w-0 lg:pl-52">
    <AppSidebar admin={admin} user={user}/><AppHeader admin={admin} user={user} onMenu={() => setMobileOpen(true)}/>
    <Dialog.Root open={mobileOpen} onOpenChange={setMobileOpen}><Dialog.Portal><Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/40 lg:hidden"/><Dialog.Content aria-label="Menu điều hướng" className="fixed inset-y-0 left-0 z-50 lg:hidden"><Dialog.Title className="sr-only">Menu điều hướng</Dialog.Title><AppSidebar admin={admin} user={user} mobile onClose={() => setMobileOpen(false)}/></Dialog.Content></Dialog.Portal></Dialog.Root>
    <main className="min-w-0 overflow-x-hidden"><div className="page-container">{children}</div></main>
  </div>;
}
