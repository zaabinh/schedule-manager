"use client";
import { useQuery } from "@tanstack/react-query";
import { businessRoleService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { ResourceList } from "@/components/admin/resource-list";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
export default function RolesPage() { const query = useQuery({ queryKey: ["business-roles"], queryFn: () => businessRoleService.list() }); if (query.isLoading) return <LoadingSkeleton/>; if (query.isError) return <ErrorState retry={() => query.refetch()}/>; return <><PageHeader eyebrow="Vai trò nghiệp vụ" title="Vai trò" description="Business Role dùng để phân loại và xác định nội dung liên quan; không cấp quyền quản trị."/><ResourceList title="Vai trò" rows={(query.data ?? []).map((r) => ({ ...r, protected: r.isProtected }))} onCreate={async (input) => { await businessRoleService.create(input); await query.refetch(); }} onUpdate={async (row, input) => { await businessRoleService.update(row.id, input); await query.refetch(); }}/></>; }
