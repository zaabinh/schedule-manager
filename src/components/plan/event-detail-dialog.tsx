"use client";

import { BellPlus, Calendar, Clock3, MapPin, StickyNote } from "lucide-react";
import { useState } from "react";
import type { EventItem } from "@/types/domain";
import { reminderService } from "@/services";
import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/utils";

type Preset = "MINUTES_15" | "MINUTES_30" | "HOUR_1" | "DAY_1" | "CUSTOM";
const options: { value: Preset; label: string }[] = [
  { value: "MINUTES_15", label: "15 phút trước" },
  { value: "MINUTES_30", label: "30 phút trước" },
  { value: "HOUR_1", label: "1 giờ trước" },
  { value: "DAY_1", label: "1 ngày trước" },
  { value: "CUSTOM", label: "Tùy chỉnh" },
];

export function EventDetailDialog({ event, trigger }: { event: EventItem; trigger: React.ReactNode }) {
  const [reminderOpen, setReminderOpen] = useState(false);
  const [preset, setPreset] = useState<Preset>(event.startDate && event.startTime ? "MINUTES_30" : "CUSTOM");
  const [customTime, setCustomTime] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const hasDateTime = Boolean(event.startDate && event.startTime);

  async function createReminder() {
    setSaving(true);
    setError("");
    try {
      const remindAt = preset === "CUSTOM" && customTime ? new Date(customTime).toISOString() : undefined;
      await reminderService.create(event, preset, remindAt);
      setReminderOpen(false);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Không thể tạo nhắc lịch.");
    } finally {
      setSaving(false);
    }
  }

  return <>
    <Dialog trigger={trigger} title={event.content} description="Chi tiết sự kiện trong kế hoạch tuần">
      <dl className="space-y-3 text-sm">
        {event.startDate && <Detail icon={<Calendar/>} label="Ngày" value={`${formatDate(event.startDate)}${event.endDate && event.endDate !== event.startDate ? ` – ${formatDate(event.endDate)}` : ""}`} />}
        {event.session && <Detail icon={<Clock3/>} label="Buổi" value={event.session === "MORNING" ? "Buổi sáng" : "Buổi chiều"} />}
        {event.startTime && <Detail icon={<Clock3/>} label="Thời gian" value={`${event.startTime}${event.endTime ? ` – ${event.endTime}` : ""}`} />}
        {event.location && <Detail icon={<MapPin/>} label="Địa điểm" value={event.location} />}
        {event.note && <Detail icon={<StickyNote/>} label="Ghi chú" value={event.note} />}
      </dl>
      <Button className="mt-6 w-full sm:w-auto" onClick={() => setReminderOpen(true)}><BellPlus size={17}/>Nhắc tôi qua email</Button>
    </Dialog>
    <Dialog open={reminderOpen} onOpenChange={setReminderOpen} title="Tạo nhắc lịch" description={event.content}>
      <div className="space-y-4">
        {hasDateTime && <fieldset>
          <legend className="field-label">Nhắc tôi</legend>
          <div className="space-y-2">{options.map((option) => <label key={option.value} className="flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm">
            <input type="radio" name={`reminder-${event.id}`} checked={preset === option.value} onChange={() => setPreset(option.value)}/>{option.label}
          </label>)}</div>
        </fieldset>}
        {(!hasDateTime || preset === "CUSTOM") && <label><span className="field-label">Ngày giờ nhắc cụ thể</span><input className="field" type="datetime-local" required value={customTime} onChange={(value) => setCustomTime(value.target.value)}/></label>}
        {error && <p role="alert" className="text-sm font-medium text-red-700">{error}</p>}
        <div className="flex justify-end gap-2"><Button variant="secondary" onClick={() => setReminderOpen(false)}>Hủy</Button><Button disabled={saving || (preset === "CUSTOM" && !customTime)} onClick={createReminder}>{saving ? "Đang tạo..." : "Tạo nhắc lịch"}</Button></div>
      </div>
    </Dialog>
  </>;
}

function Detail({ icon, label, value }: { icon: React.ReactElement; label: string; value: string }) {
  return <div className="flex gap-3 rounded-xl bg-slate-50 p-3"><span className="mt-0.5 text-slate-400">{icon}</span><div><dt className="text-xs font-semibold text-slate-500">{label}</dt><dd className="mt-0.5 font-medium text-slate-800">{value}</dd></div></div>;
}
