import { PageHeader } from "@/components/layout/page-header";
import { ConversationWorkspace } from "@/components/conversation/conversation-workspace";
export default function AdminConversationsPage() { return <><PageHeader eyebrow="Hộp thư nhà trường" title="Trao đổi" description="Phản hồi giáo viên và đóng cuộc trao đổi sau khi đã xử lý."/><ConversationWorkspace admin/></>; }
