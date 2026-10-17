package ch.menueplan.survey;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Einfache In-Memory-Bremse gegen Spam: höchstens N Antworten pro IP und Stunde. Reicht für eine Freundes-Umfrage. */
@ApplicationScoped
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    @ConfigProperty(name = "survey.rate-limit.per-hour", defaultValue = "5")
    int perHour;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        Instant now = Instant.now();
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
                q.pollFirst();
            }
            if (q.size() >= perHour) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
