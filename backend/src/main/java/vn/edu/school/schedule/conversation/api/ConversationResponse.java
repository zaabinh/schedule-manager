package vn.edu.school.schedule.conversation.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(UUID id, String subject, String category, String status, UUID createdBy,
                                   Instant updatedAt, long version, List<ConversationMessageResponse> messages) { }
