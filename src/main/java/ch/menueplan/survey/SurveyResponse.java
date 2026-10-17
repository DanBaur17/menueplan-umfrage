package ch.menueplan.survey;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Eine abgeschickte Umfrage-Antwort. Die Rohantworten liegen als JSON-Text, die wichtigsten Signale zusätzlich als Spalten. */
@Entity
@Table(name = "survey_response")
public class SurveyResponse extends PanacheEntity {

    @Column(nullable = false)
    public Instant createdAt;

    @Column(length = 100)
    public String name;

    @Column(length = 200)
    public String contact;

    /** Testet den Prototyp: "Ja, vor Ort" | "Ja, per Video" | "Nein" | null */
    @Column(length = 50)
    public String prototypeTest;

    /** Nutzt erste Version 4 Wochen: "Ja" | "Vielleicht" | "Nein" | null */
    @Column(length = 50)
    public String betaUse;

    /** Reserviert Jahresabo: "Ja" | "Vielleicht" | "Nein" | null */
    @Column(length = 50)
    public String preorder;

    /** Gewünschte Stufe: "Free" | "Midi" | "Pro" | "Keine" | null */
    @Column(length = 50)
    public String tier;

    @Column(length = 20000, nullable = false)
    public String answersJson;

    @Column(length = 4000, nullable = false)
    public String ratingsJson;

    @Column(length = 20000)
    public String summary;
}
