import { PageHeader } from "@/components/layout/page-header";
import { ConversationWorkspace } from "@/components/conversation/conversation-workspace";
export default function ConversationsPage() { return <><PageHeader eyebrow="Hỗ trợ và trao đổi" title="Trao đổi với Hiệu trưởng" description="Gửi câu hỏi và theo dõi phản hồi. Dữ liệu được cập nhật khi bạn tải lại trang."/><ConversationWorkspace/></>; }
