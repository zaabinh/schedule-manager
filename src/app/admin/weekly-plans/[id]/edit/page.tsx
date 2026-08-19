"use client";

import { useParams } from "next/navigation";
import { WeeklyPlanEditor } from "@/components/plan/weekly-plan-editor";
export default function EditWeeklyPlanPage() { const params = useParams<{ id: string }>(); return <WeeklyPlanEditor weekId={params.id}/>; }
