package com.tuganire.conversation.dto;

/**
 * Response for {@code POST /api/v1/sessions}.
 *
 * @param sessionId
 *            UUID string identifying the anonymous session
 * @param websocketUrl
 *            WebSocket upgrade path for this session
 */
public record SessionResponse(String sessionId, String websocketUrl) {
}
