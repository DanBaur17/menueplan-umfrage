package ch.menueplan.survey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Path("/api")
public class ResponseResource {

    private static final TypeReference<Map<String, Object>> ANSWERS = new TypeReference<>() { };
    private static final TypeReference<Map<String, Integer>> RATINGS = new TypeReference<>() { };

    @Inject
    ObjectMapper mapper;

    @Inject
    Notifier notifier;

    @Inject
    RateLimiter limiter;

    @ConfigProperty(name = "survey.admin-token")
    Optional<String> adminToken;

    // ---------- öffentlich ----------

    @POST
    @Path("/responses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submit(@Valid SubmitRequest req, @HeaderParam("X-Forwarded-For") String forwardedFor) throws Exception {
        // Honeypot: Bots füllen das versteckte Feld aus. So tun, als ob alles geklappt hätte.
        if (req.website() != null && !req.website().isBlank()) {
            return Response.accepted(Map.of("ok", true)).build();
        }
        String ip = forwardedFor == null ? "unknown" : forwardedFor.split(",")[0].trim();
        if (!limiter.allow(ip)) {
            return Response.status(429).entity(Map.of("ok", false, "error", "Zu viele Antworten von diesem Gerät. Versuch es später nochmals.")).build();
        }

        SurveyResponse saved = QuarkusTransaction.requiringNew().call(() -> {
            SurveyResponse r = new SurveyResponse();
            r.createdAt = Instant.now();
            r.name = trimTo(asText(req.answers().get("name")), 100);
            r.contact = trimTo(asText(req.answers().get("kontakt")), 200);
            r.prototypeTest = trimTo(asText(req.answers().get("proto")), 50);
            r.betaUse = trimTo(asText(req.answers().get("beta")), 50);
            r.preorder = trimTo(asText(req.answers().get("vorbest")), 50);
            r.tier = trimTo(asText(req.answers().get("stufe")), 50);
            r.answersJson = trimTo(mapper.writeValueAsString(req.answers()), 20000);
            r.ratingsJson = trimTo(mapper.writeValueAsString(req.ratings()), 4000);
            r.summary = trimTo(req.summary(), 20000);
            r.persist();
            return r;
        });

        notifier.sendAsync(saved);
        return Response.status(Response.Status.CREATED).entity(Map.of("ok", true, "id", saved.id)).build();
    }

    // ---------- nur für Daniel (Token) ----------

    @GET
    @Path("/responses")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> list(@HeaderParam("X-Admin-Token") String header, @QueryParam("token") String query) throws Exception {
        requireAdmin(header, query);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SurveyResponse r : all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("createdAt", r.createdAt.toString());
            m.put("name", r.name);
            m.put("contact", r.contact);
            m.put("prototypeTest", r.prototypeTest);
            m.put("betaUse", r.betaUse);
            m.put("preorder", r.preorder);
            m.put("tier", r.tier);
            m.put("answers", mapper.readValue(r.answersJson, ANSWERS));
            m.put("ratings", mapper.readValue(r.ratingsJson, RATINGS));
            out.add(m);
        }
        return out;
    }

    @GET
    @Path("/responses.csv")
    @Produces("text/csv; charset=UTF-8")
    public Response csv(@HeaderParam("X-Admin-Token") String header, @QueryParam("token") String query) throws Exception {
        requireAdmin(header, query);
        List<SurveyResponse> rows = all();
        // Spalten: feste Signale + alle vorkommenden Frage-IDs + alle Feature-IDs
        TreeMap<String, Boolean> qKeys = new TreeMap<>();
        TreeMap<String, Boolean> fKeys = new TreeMap<>();
        List<Map<String, Object>> answers = new ArrayList<>();
        List<Map<String, Integer>> ratings = new ArrayList<>();
        for (SurveyResponse r : rows) {
            Map<String, Object> a = mapper.readValue(r.answersJson, ANSWERS);
            Map<String, Integer> f = mapper.readValue(r.ratingsJson, RATINGS);
            a.keySet().forEach(k -> qKeys.put(k, true));
            f.keySet().forEach(k -> fKeys.put(k, true));
            answers.add(a);
            ratings.add(f);
        }
        StringBuilder sb = new StringBuilder("﻿"); // BOM, damit Excel Umlaute richtig liest
        List<String> head = new ArrayList<>(List.of("id", "datum"));
        qKeys.keySet().forEach(head::add);
        fKeys.keySet().forEach(k -> head.add("feature_" + k));
        sb.append(String.join(";", head)).append("\n");
        for (int i = 0; i < rows.size(); i++) {
            List<String> cells = new ArrayList<>();
            cells.add(String.valueOf(rows.get(i).id));
            cells.add(rows.get(i).createdAt.toString());
            for (String k : qKeys.keySet()) cells.add(csvCell(asText(answers.get(i).get(k))));
            for (String k : fKeys.keySet()) {
                Integer v = ratings.get(i).get(k);
                cells.add(v == null ? "" : String.valueOf(v));
            }
            sb.append(String.join(";", cells)).append("\n");
        }
        return Response.ok(sb.toString())
                .header("Content-Disposition", "attachment; filename=\"menueplan-umfrage.csv\"")
                .build();
    }

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> stats(@HeaderParam("X-Admin-Token") String header, @QueryParam("token") String query) throws Exception {
        requireAdmin(header, query);
        List<SurveyResponse> rows = all();
        Map<String, Map<String, Integer>> optionCounts = new TreeMap<>();
        Map<String, int[]> featureCounts = new TreeMap<>(); // [brauch ich nicht, nett, will ich]
        Map<String, List<Double>> prices = new TreeMap<>();
        int proto = 0, beta = 0, preorder = 0;

        for (SurveyResponse r : rows) {
            if ("Ja".equals(r.prototypeTest) || "Nur kurz".equals(r.prototypeTest)) proto++;
            if ("Ja".equals(r.betaUse)) beta++;
            if ("Ja".equals(r.preorder)) preorder++;
            Map<String, Object> a = mapper.readValue(r.answersJson, ANSWERS);
            for (Map.Entry<String, Object> e : a.entrySet()) {
                if (List.of("name", "kontakt", "fehlt", "proto_gut", "proto_schlecht").contains(e.getKey())) continue;
                Collection<?> values = e.getValue() instanceof Collection<?> c ? c : List.of(String.valueOf(e.getValue()));
                for (Object v : values) {
                    optionCounts.computeIfAbsent(e.getKey(), k -> new TreeMap<>()).merge(String.valueOf(v), 1, Integer::sum);
                    if (e.getKey().startsWith("p_")) {
                        Double p = priceOf(String.valueOf(v));
                        if (p != null) prices.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(p);
                    }
                }
            }
            Map<String, Integer> f = mapper.readValue(r.ratingsJson, RATINGS);
            f.forEach((k, v) -> {
                if (v != null && v >= 0 && v <= 2) featureCounts.computeIfAbsent(k, x -> new int[3])[v]++;
            });
        }

        Map<String, Object> features = new TreeMap<>();
        featureCounts.forEach((k, c) -> features.put(k, Map.of("brauchIchNicht", c[0], "nett", c[1], "willIch", c[2])));
        Map<String, Double> priceMedians = new TreeMap<>();
        prices.forEach((k, list) -> priceMedians.put(k, median(list)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("antworten", rows.size());
        out.put("signale", Map.of("prototypAusprobiert", proto, "betaJa", beta, "vorbestellungJa", preorder));
        out.put("preisMedianeChf", priceMedians);
        out.put("features", features);
        out.put("fragen", optionCounts);
        out.put("mailAktiv", notifier.enabled());
        return out;
    }

    // ---------- Hilfen ----------

    private List<SurveyResponse> all() {
        return SurveyResponse.listAll(Sort.descending("createdAt"));
    }

    private void requireAdmin(String header, String query) {
        String expected = adminToken.filter(t -> !t.isBlank()).orElse(null);
        String given = header != null ? header : query;
        if (expected == null || given == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException();
        }
    }

    static String asText(Object v) {
        if (v == null) return null;
        if (v instanceof Collection<?> c) return String.join(", ", c.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    static String trimTo(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    static String csvCell(String s) {
        if (s == null) return "";
        String v = s.replace("\r", " ").replace("\n", " ");
        // Schutz vor Formel-Injektion in Excel
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    static Double priceOf(String label) {
        String digits = label.replace("CHF", "").replace("+", "").replace(".–", "").trim();
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static double median(List<Double> values) {
        List<Double> s = values.stream().sorted().toList();
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }
}
