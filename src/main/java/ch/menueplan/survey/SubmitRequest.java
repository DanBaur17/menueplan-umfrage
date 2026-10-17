package ch.menueplan.survey;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Payload der Umfrage-Seite.
 *
 * @param answers Antworten je Frage-ID: Text oder Liste von Texten (Mehrfachauswahl)
 * @param ratings Feature-ID → 0 (brauch ich nicht), 1 (nett), 2 (will ich)
 * @param summary lesbare Zusammenfassung, wie sie die Seite anzeigt
 * @param website Honeypot-Feld, bleibt bei echten Menschen leer
 */
public record SubmitRequest(
        @NotNull Map<String, Object> answers,
        @NotNull Map<String, Integer> ratings,
        @Size(max = 20000) String summary,
        String website) {
}
