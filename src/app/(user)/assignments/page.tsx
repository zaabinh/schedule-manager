"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { taskService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { TaskCard, getDisplayStatus } from "@/components/task/task-card";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";
import { cn } from "@/lib/utils";
import type { DisplayTaskStatus } from "@/types/domain";

const tabs: { label: string; value: "ALL" | DisplayTaskStatus }[] = [{ label: "Tất cả", value: "ALL" }, { label: "Chưa hoàn thành", value: "TODO" }, { label: "Đã hoàn thành", value: "COMPLETED" }, { label: "Quá hạn", value: "OVERDUE" }];
export default function AssignmentsPage() { const [filter, setFilter] = useState<(typeof tabs)[number]["value"]>("ALL"); const client = useQueryClient(); const query = useQuery({ queryKey: ["tasks", "mine"], queryFn: () => taskService.listMine() }); if (query.isLoading) return <LoadingSkeleton/>; if (query.isError) return <ErrorState retry={() => query.refetch()}/>; const filtered = (query.data ?? []).filter((task) => filter === "ALL" || getDisplayStatus(task) === filter); async function complete(id: string) { await taskService.complete(id); await client.invalidateQueries({ queryKey: ["tasks"] }); } return <><PageHeader eyebrow="Công việc cá nhân" title="Phân công của tôi" description="Theo dõi thời hạn và hoàn thành những nhiệm vụ được giao."/><div className="mb-6 flex gap-2 overflow-x-auto pb-1" role="tablist">{tabs.map((tab) => <button role="tab" aria-selected={filter === tab.value} onClick={() => setFilter(tab.value)} key={tab.value} className={cn("whitespace-nowrap rounded-full border bg-white px-4 py-2 text-sm font-semibold", filter === tab.value && "border-[var(--primary)] bg-[var(--primary)] text-white")}>{tab.label}</button>)}</div>{filtered.length ? <div className="grid gap-4 lg:grid-cols-2">{filtered.map((task) => <TaskCard key={task.id} task={task} onComplete={complete}/>)}</div> : <EmptyState title="Không có nhiệm vụ" description="Không có nhiệm vụ nào phù hợp với bộ lọc hiện tại."/>}</>; }
