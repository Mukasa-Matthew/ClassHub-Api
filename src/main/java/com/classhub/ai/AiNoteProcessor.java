package com.classhub.ai;

/**
 * Provider-agnostic AI note processing boundary.
 * Implementations must never mutate the caller's raw note text.
 */
public interface AiNoteProcessor {

    AiNoteResult process(AiNoteRequest request);
}
