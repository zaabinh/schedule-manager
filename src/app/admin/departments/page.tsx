"use client";
import { useQuery } from "@tanstack/react-query";
import { departmentService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { ResourceList } from "@/components/admin/resource-list";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
export default function DepartmentsPage() { const query = useQuery({ queryKey: ["departments"], queryFn: () => departmentService.list() }); if (query.isLoading) return <LoadingSkeleton/>; if (query.isError) return <ErrorState retry={() => query.refetch()}/>; return <><PageHeader eyebrow="Cơ cấu nhà trường" title="Phòng ban" description="Thêm, chỉnh sửa và vô hiệu hóa phòng ban. Dữ liệu đã được tham chiếu không bị xóa."/><ResourceList title="Phòng ban" rows={(query.data ?? []).map((d) => ({ ...d }))} onCreate={async (input) => { await departmentService.create(input); await query.refetch(); }} onUpdate={async (row, input) => { await departmentService.update(row.id, input); await query.refetch(); }}/></>; }
