"use client";

import { Send, XCircle } from "lucide-react";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { authService, conversationService } from "@/services";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/ui/status-badge";
import { cn, formatDateTime } from "@/lib/utils";

export function ConversationWorkspace({ admin = false }: { admin?: boolean }) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ["conversations"], queryFn: () => conversationService.list(), refetchInterval: 15_000 });
  const me = useQuery({ queryKey: ["auth", "me"], queryFn: () => authService.getCurrentUser() });
  const [selected, setSelected] = useState<string>();
  const [message, setMessage] = useState("");
  const [newOpen, setNewOpen] = useState(false);
  const [subject, setSubject] = useState("");
  const [firstMessage, setFirstMessage] = useState("");
  const selectedId = selected ?? query.data?.[0]?.id;
  const active = query.data?.find((conversation) => conversation.id === selectedId);

  async function send() {
    if (!active || !message.trim()) return;
    await conversationService.sendMessage(active.id, message.trim());
    setMessage("");
    await client.invalidateQueries({ queryKey: ["conversations"] });
  }
  async function close() {
    if (!active) return;
    await conversationService.close(active.id);
    await client.invalidateQueries({ queryKey: ["conversations"] });
  }
  async function createConversation(event: React.FormEvent) {
    event.preventDefault();
    if (!subject.trim() || !firstMessage.trim()) return;
    const created = await conversationService.create(subject.trim(), undefined, firstMessage.trim());
    setSelected(created.id); setSubject(""); setFirstMessage(""); setNewOpen(false);
    await client.invalidateQueries({ queryKey: ["conversations"] });
  }

  return <>
    <div className="grid min-h-[620px] overflow-hidden rounded-2xl border bg-white lg:grid-cols-[320px_1fr]">
      <aside className="border-b lg:border-b-0 lg:border-r">
        <div className="border-b p-4">{admin ? <p className="text-sm font-semibold text-slate-600">Các trao đổi của giáo viên</p> : <Button className="w-full" onClick={() => setNewOpen(true)}>+ Tạo trao đổi mới</Button>}</div>
        <div className="max-h-64 overflow-y-auto lg:max-h-[550px]">{query.data?.map((item) => <button key={item.id} onClick={() => setSelected(item.id)} className={cn("block w-full border-b p-4 text-left hover:bg-slate-50", selectedId === item.id && "bg-[var(--primary-soft)]")}><span className="block truncate text-sm font-bold">{item.subject}</span><span className="mt-2 flex items-center justify-between gap-2"><StatusBadge status={item.status}/><time className="text-[11px] text-slate-600">{formatDateTime(item.updatedAt)}</time></span></button>)}</div>
      </aside>
      {active ? <section className="flex min-h-0 flex-col">
        <header className="flex items-center justify-between gap-3 border-b p-4"><div><h2 className="font-bold">{active.subject}</h2><p className="text-xs text-slate-500">Cập nhật bằng cách tải lại, không sử dụng realtime.</p></div>{admin && active.status === "OPEN" && <Button variant="secondary" size="sm" onClick={close}><XCircle size={16}/>Đóng trao đổi</Button>}</header>
        <div className="flex-1 space-y-4 overflow-y-auto bg-slate-50/60 p-4 sm:p-6">{active.messages.map((item) => { const mine = item.senderId === me.data?.id; return <div key={item.id} className={cn("flex", mine ? "justify-end" : "justify-start")}><div className={cn("max-w-[82%] rounded-2xl px-4 py-3", mine ? "rounded-br-sm bg-[var(--primary)] text-white" : "rounded-bl-sm border bg-white")}><p className={cn("mb-1 text-xs font-bold", mine ? "text-emerald-100" : "text-slate-500")}>{item.senderName}</p><p className="text-sm leading-6">{item.content}</p><time className={cn("mt-2 block text-[10px]", mine ? "text-emerald-50" : "text-slate-500")}>{formatDateTime(item.createdAt)}</time></div></div>; })}</div>
        <div className="border-t p-4"><div className="flex gap-2"><label className="sr-only" htmlFor="message">Tin nhắn</label><textarea id="message" className="field min-h-12 resize-none" placeholder={active.status === "CLOSED" ? "Trao đổi đã đóng" : "Nhập nội dung trao đổi…"} disabled={active.status === "CLOSED"} value={message} onChange={(event) => setMessage(event.target.value)}/><Button aria-label="Gửi tin nhắn" disabled={active.status === "CLOSED" || !message.trim()} onClick={send}><Send size={18}/><span className="hidden sm:inline">Gửi</span></Button></div></div>
      </section> : <div className="grid place-items-center text-sm text-slate-500">Chọn một cuộc trao đổi</div>}
    </div>
    <Dialog open={newOpen} onOpenChange={setNewOpen} title="Tạo trao đổi mới" description="Gửi câu hỏi hoặc nội dung cần Hiệu trưởng hỗ trợ."><form className="space-y-4" onSubmit={createConversation}><label><span className="field-label">Chủ đề *</span><input className="field" required maxLength={255} value={subject} onChange={(event) => setSubject(event.target.value)}/></label><label><span className="field-label">Nội dung *</span><textarea className="field min-h-32" required maxLength={3000} value={firstMessage} onChange={(event) => setFirstMessage(event.target.value)}/></label><div className="dialog-actions"><Button type="button" variant="secondary" onClick={() => setNewOpen(false)}>Hủy</Button><Button type="submit">Gửi trao đổi</Button></div></form></Dialog>
  </>;
}
