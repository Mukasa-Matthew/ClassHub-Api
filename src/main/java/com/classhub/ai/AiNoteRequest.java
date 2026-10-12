package com.classhub.ai;

/**
 * Minimal context passed to an AI note processor. Keep this free of secrets and unrelated user data.
 */
public record AiNoteRequest(String rawContent, String courseUnitName, AiNoteOperation operation) {
}
