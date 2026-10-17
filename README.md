# survey-service

Kleiner Quarkus-Service für die Menüplan-Umfrage. Er liefert die Umfrage-Seite aus, speichert jede Antwort in PostgreSQL und schickt dir pro Antwort eine Mail.

## Endpunkte

| Methode | Pfad | Zugriff | Zweck |
| --- | --- | --- | --- |
| GET | `/` | öffentlich | Umfrage-Seite |
| POST | `/api/responses` | öffentlich | Antwort speichern (Honeypot + max. 5 pro IP und Stunde) |
| GET | `/api/stats` | Token | Auswertung: Signale, Preis-Mediane, Feature-Bewertungen, Antwortverteilung |
| GET | `/api/responses` | Token | Alle Antworten als JSON |
| GET | `/api/responses.csv` | Token | Export für Excel/Numbers (Semikolon, UTF-8 mit BOM) |

Token per Header `X-Admin-Token: …` oder im Browser als `?token=…`, z. B. `https://<domain>/api/stats?token=…`.

## Lokal starten

```bash
mvn quarkus:dev           # startet Postgres per Dev Services (Docker nötig)
mvn verify                # Tests laufen mit H2, ohne Docker
```

Danach: http://localhost:8080

## Deploy auf Railway

1. Repo auf GitHub pushen, in Railway **New Project → Deploy from GitHub repo**. Railway baut mit dem `Dockerfile`.
2. Im selben Projekt **+ New → Database → PostgreSQL** anlegen.
3. Beim Service unter **Variables** setzen:

| Variable | Wert |
| --- | --- |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | als Referenz auf die Postgres-Variablen, z. B. `${{Postgres.PGHOST}}` |
| `ADMIN_TOKEN` | langer Zufallswert, z. B. `openssl rand -hex 24` |
| `RESEND_API_KEY` | API-Key von resend.com (optional, ohne Key keine Mails) |
| `NOTIFY_TO` | deine Mailadresse |
| `NOTIFY_FROM` | optional, Standard `Menüplan Umfrage <onboarding@resend.dev>` |

4. **Settings → Networking → Generate Domain**. Diese URL verschickst du an deine Freunde.

## Mail

Versand über die HTTP-API von [Resend](https://resend.com), nicht über SMTP, weil Railway ausgehendes SMTP oft blockiert.

- Gratis-Konto anlegen, API-Key erstellen.
- Mit dem Absender `onboarding@resend.dev` gehen Mails nur an die Adresse, mit der du bei Resend registriert bist. Das reicht, solange nur du benachrichtigt wirst. Für einen eigenen Absender musst du bei Resend eine Domain verifizieren.
- Schlägt der Versand fehl, ist die Antwort trotzdem gespeichert. Der Fehler steht im Railway-Log.

## Umfrage-Seite anpassen

Die Seite liegt unter `src/main/resources/META-INF/resources/index.html`. Oben im Script:

- `PROTO_URL`: Link zum Prototyp (muss öffentlich geteilt sein)
- `MAIL_TO`: Adresse für den Mail-Fallback, falls das Senden scheitert
- `API_URL`: leer lassen, solange der Service die Seite selbst ausliefert

Läuft die Seite ohne Backend (claude.ai, lokale Datei), zeigt sie automatisch nur „Per Mail senden“ und „Text kopieren“.

## Datenschutz

Gespeichert werden nur die Antworten und freiwillig angegebener Name und Kontakt, keine IP-Adressen. Die IP dient nur kurz im Speicher für die Spam-Bremse. Lösch die Datenbank nach der Auswertung.
