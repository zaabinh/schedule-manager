"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { NotificationItem } from "@/components/notification/notification-item";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";

export default function NotificationsPage() { const [unreadOnly, setUnreadOnly] = useState(false); const client = useQueryClient(); const query = useQuery({ queryKey: ["notifications"], queryFn: () => notificationService.list() }); if (query.isLoading) return <LoadingSkeleton/>; if (query.isError) return <ErrorState retry={() => query.refetch()}/>; const data = (query.data ?? []).filter((n) => !unreadOnly || !n.readAt); async function refresh(action: Promise<unknown>) { await action; await client.invalidateQueries({ queryKey: ["notifications"] }); } return <><PageHeader eyebrow="Trung tâm thông báo" title="Thông báo" description="Các cập nhật về kế hoạch, nhiệm vụ, lớp trực và trao đổi." actions={<Button variant="secondary" onClick={() => refresh(notificationService.markAllRead())}>Đánh dấu tất cả đã đọc</Button>}/><div className="mb-5 flex gap-2"><Button variant={!unreadOnly ? "primary" : "secondary"} onClick={() => setUnreadOnly(false)}>Tất cả</Button><Button variant={unreadOnly ? "primary" : "secondary"} onClick={() => setUnreadOnly(true)}>Chưa đọc</Button></div>{data.length ? <div className="space-y-3">{data.map((item) => <NotificationItem key={item.id} item={item} onRead={(id) => refresh(notificationService.markRead(id))}/>)}</div> : <EmptyState title="Không có thông báo" description="Bạn đã đọc hết các thông báo trong danh sách này."/>}</>; }
