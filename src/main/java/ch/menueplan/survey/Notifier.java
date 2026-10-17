package ch.menueplan.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schickt pro Antwort eine Mail über die HTTP-API von Resend (https://resend.com).
 * Bewusst kein SMTP: Railway blockiert ausgehendes SMTP auf vielen Plänen.
 * Ohne RESEND_API_KEY wird nur geloggt, die Antwort ist trotzdem gespeichert.
 */
@ApplicationScoped
public class Notifier {

    private static final Logger LOG = Logger.getLogger(Notifier.class);

    @ConfigProperty(name = "notify.resend.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "notify.to")
    Optional<String> to;

    @ConfigProperty(name = "notify.from", defaultValue = "Menüplan Umfrage <onboarding@resend.dev>")
    String from;

    @Inject
    ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean enabled() {
        return apiKey.filter(k -> !k.isBlank()).isPresent() && to.filter(t -> !t.isBlank()).isPresent();
    }

    public void sendAsync(SurveyResponse r) {
        if (!enabled()) {
            LOG.infof("Antwort %d gespeichert, Mailversand nicht konfiguriert", r.id);
            return;
        }
        try {
            String subject = "Neue Umfrage-Antwort" + (r.name == null || r.name.isBlank() ? "" : " – " + r.name);
            String text = (r.summary == null ? "" : r.summary)
                    + "\n\n—\nAntwort #" + r.id + " · " + r.createdAt;
            String body = mapper.writeValueAsString(Map.of(
                    "from", from,
                    "to", List.of(to.get()),
                    "subject", subject,
                    "text", text));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.get())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        if (res.statusCode() >= 300) {
                            LOG.warnf("Mail für Antwort %d fehlgeschlagen: %d %s", r.id, res.statusCode(), res.body());
                        }
                    })
                    .exceptionally(e -> {
                        LOG.warnf(e, "Mail für Antwort %d fehlgeschlagen", r.id);
                        return null;
                    });
        } catch (Exception e) {
            LOG.warnf(e, "Mail für Antwort %d konnte nicht vorbereitet werden", r.id);
        }
    }
}
