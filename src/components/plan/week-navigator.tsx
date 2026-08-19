import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

export function WeekNavigator() { return <div className="flex items-center justify-between gap-3"><Button variant="secondary" size="sm"><ChevronLeft size={16}/>Tuần trước</Button><span className="hidden text-xs font-medium text-slate-500 sm:block">Điều hướng theo tuần học</span><Button variant="secondary" size="sm">Tuần sau<ChevronRight size={16}/></Button></div>; }
