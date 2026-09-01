package com.classhub.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic development/stub processor for architecture and tests.
 * Not a real LLM. Future providers (openai/ollama/etc.) can replace this via configuration.
 */
@Component
public class LocalAiNoteProcessor implements AiNoteProcessor {

    /** Test-only sentinel: when present in raw content, processing fails without writing output. */
    public static final String FORCE_FAILURE_MARKER = "__CLASS_HUB_AI_FAIL__";

    public static final String PROVIDER = "local";
    public static final String MODEL = "local-deterministic-v1";

    @Override
    public AiNoteResult process(AiNoteRequest request) {
        if (request.rawContent() != null && request.rawContent().contains(FORCE_FAILURE_MARKER)) {
            throw new IllegalStateException("Forced local AI processing failure");
        }

        String course = request.courseUnitName() == null ? "Course unit" : request.courseUnitName();
        String raw = request.rawContent() == null ? "" : request.rawContent().trim();
        String content = switch (request.operation()) {
            case ORGANIZE -> organize(course, raw);
            case EXPAND -> expand(course, raw);
            case SUMMARIZE -> summarize(course, raw);
            case EXPLAIN -> explain(course, raw);
            case CORRECT -> correct(course, raw);
            case STUDY_GUIDE -> studyGuide(course, raw);
        };
        return new AiNoteResult(content, PROVIDER, MODEL);
    }

    private static String organize(String course, String raw) {
        String bullets = Arrays.stream(raw.split("(?<=[.!?])\\s+|\\n+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));
        return "# " + course + " — Organized notes\n\n" + (bullets.isEmpty() ? "- (empty note)" : bullets);
    }

    private static String expand(String course, String raw) {
        return "# " + course + " — Expanded notes\n\n"
                + raw
                + "\n\n## AI-generated expansion\n"
                + "Additional educational context may be added here by a real model later. "
                + "This local processor only marks that expansion was requested.";
    }

    private static String summarize(String course, String raw) {
        String first = raw.isEmpty() ? "(empty note)" : raw.split("(?<=[.!?])\\s+|\\n+")[0].trim();
        int words = raw.isEmpty() ? 0 : raw.trim().split("\\s+").length;
        return "# " + course + " — Summary\n\n"
                + first
                + "\n\n(Word count: " + words + ")";
    }

    private static String explain(String course, String raw) {
        return "# " + course + " — Explanation\n\n"
                + "The following notes are restated more clearly by the local processor:\n\n"
                + raw;
    }

    private static String correct(String course, String raw) {
        String corrected = raw.replaceAll("\\s+", " ").trim();
        if (!corrected.isEmpty()) {
            corrected = corrected.substring(0, 1).toUpperCase(Locale.ROOT) + corrected.substring(1);
            if (!corrected.endsWith(".") && !corrected.endsWith("!") && !corrected.endsWith("?")) {
                corrected = corrected + ".";
            }
        }
        return "# " + course + " — Corrected notes\n\n" + corrected;
    }

    private static String studyGuide(String course, String raw) {
        return "# " + course + " — Study guide\n\n"
                + "## Key points\n"
                + Arrays.stream(raw.split("(?<=[.!?])\\s+|\\n+"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(8)
                        .map(s -> "- " + s)
                        .collect(Collectors.joining("\n"))
                + "\n\n## Review tip\nRevisit these points before the next lecture.";
    }
}
