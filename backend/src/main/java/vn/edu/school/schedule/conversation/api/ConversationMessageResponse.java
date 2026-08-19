package vn.edu.school.schedule.conversation.api;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageResponse(UUID id, UUID senderId, String senderName, String content, Instant createdAt) { }
