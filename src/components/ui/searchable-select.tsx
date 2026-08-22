"use client";

import * as Popover from "@radix-ui/react-popover";
import { Check, ChevronsUpDown, Search, X } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { filterSelectOptions, type SearchableSelectOption } from "./searchable-select-utils";

export function SearchableSelect({
  value,
  options,
  onValueChange,
  placeholder = "Chọn một mục",
  searchPlaceholder = "Nhập để tìm…",
  emptyText = "Không tìm thấy kết quả.",
  ariaLabel,
  disabled = false,
  clearable = false,
  className,
}: {
  value: string;
  options: readonly SearchableSelectOption[];
  onValueChange: (value: string) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  ariaLabel: string;
  disabled?: boolean;
  clearable?: boolean;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);
  const selected = options.find((option) => option.value === value);
  const filtered = useMemo(() => filterSelectOptions(options, query), [options, query]);

  function changeOpen(next: boolean) {
    setOpen(next);
    if (!next) setQuery("");
  }

  return <Popover.Root open={open} onOpenChange={changeOpen}>
    <div className={cn("relative", className)}>
      <Popover.Trigger asChild>
        <button
          type="button"
          aria-label={ariaLabel}
          aria-expanded={open}
          aria-haspopup="listbox"
          disabled={disabled}
          className={cn(
            "field flex min-h-11 items-center justify-between gap-3 text-left transition",
            "hover:border-slate-400 disabled:cursor-not-allowed disabled:bg-slate-50",
            open && "border-[var(--primary)] ring-3 ring-emerald-900/10",
            !selected && "text-slate-400",
            clearable && selected && "pr-18",
          )}
        >
          <span className="min-w-0 flex-1 truncate">{selected?.label ?? placeholder}</span>
          <ChevronsUpDown aria-hidden size={17} className="shrink-0 text-slate-400"/>
        </button>
      </Popover.Trigger>
      {clearable && selected && !disabled && <button
        type="button"
        aria-label={`Bỏ chọn ${selected.label}`}
        onClick={() => onValueChange("")}
        className="absolute right-9 top-1/2 z-10 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
      ><X size={14}/></button>}
    </div>
    <Popover.Portal>
      <Popover.Content
        align="start"
        sideOffset={6}
        collisionPadding={12}
        className="z-[70] w-[var(--radix-popover-trigger-width)] min-w-60 overflow-hidden rounded-xl border border-slate-200 bg-white p-1.5 shadow-[0_16px_40px_rgba(15,23,42,.16)]"
        onOpenAutoFocus={(event) => { event.preventDefault(); searchRef.current?.focus(); }}
      >
        <div className="relative mb-1.5">
          <Search aria-hidden size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"/>
          <input
            ref={searchRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") { event.preventDefault(); changeOpen(false); }
              if (event.key === "Enter" && filtered[0]) {
                event.preventDefault();
                onValueChange(filtered[0].value);
                changeOpen(false);
              }
            }}
            placeholder={searchPlaceholder}
            aria-label={`Tìm trong ${ariaLabel.toLocaleLowerCase("vi")}`}
            className="h-10 w-full rounded-lg border-0 bg-slate-50 pl-9 pr-3 text-sm outline-none ring-0 placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-emerald-900/15"
          />
        </div>
        <div role="listbox" aria-label={ariaLabel} className="max-h-64 overflow-y-auto overscroll-contain">
          {filtered.length === 0 && <p className="px-3 py-6 text-center text-sm text-slate-500">{emptyText}</p>}
          {filtered.map((option) => {
            const active = option.value === value;
            return <button
              type="button"
              role="option"
              data-value={option.value}
              aria-selected={active}
              key={option.value}
              onClick={() => { onValueChange(option.value); changeOpen(false); }}
              className={cn(
                "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm outline-none transition hover:bg-slate-50 focus-visible:bg-slate-100",
                active && "bg-[var(--primary-soft)] text-[var(--primary-strong)]",
              )}
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold">{option.label}</span>
                {option.description && <span className="mt-0.5 block truncate text-xs font-normal text-slate-500">{option.description}</span>}
              </span>
              <Check aria-hidden size={16} className={cn("shrink-0", active ? "opacity-100" : "opacity-0")}/>
            </button>;
          })}
        </div>
        <div className="border-t px-3 py-2 text-[11px] text-slate-400">Gõ để lọc · Chọn một kết quả</div>
      </Popover.Content>
    </Popover.Portal>
  </Popover.Root>;
}
