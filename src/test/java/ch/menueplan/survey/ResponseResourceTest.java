package ch.menueplan.survey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ResponseResourceTest {

    private static final String BODY = """
            {
              "answers": {
                "name": "Anna", "kontakt": "079 000 00 00",
                "wer": "Ich", "nerv": ["Keine Ideen, immer dasselbe", "Reste werden weggeworfen"],
                "tm": "Ja", "tm_eigen": "Manchmal",
                "p_guenstig": "5", "p_teuer": "10",
                "proto": "Ja", "beta": "Ja", "vorbest": "Vielleicht", "stufe": "Midi",
                "fehlt": "=HYPERLINK(\\"x\\")"
              },
              "ratings": { "ki": 2, "kal": 2, "tmconv": 0 },
              "summary": "Menüplan-Umfrage – Anna"
            }
            """;

    @Test
    void speichertAntwortUndLiefertStatistik() {
        given().contentType(ContentType.JSON).header("X-Forwarded-For", "10.0.0.1").body(BODY)
                .when().post("/api/responses")
                .then().statusCode(201).body("ok", equalTo(true)).body("id", notNullValue());

        given().header("X-Admin-Token", "test-token")
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("antworten", greaterThanOrEqualTo(1))
                .body("signale.prototypAusprobiert", greaterThanOrEqualTo(1))
                .body("features.ki.willIch", greaterThanOrEqualTo(1))
                .body("fragen.nerv.'Reste werden weggeworfen'", greaterThanOrEqualTo(1));

        given().queryParam("token", "test-token")
                .when().get("/api/responses.csv")
                .then().statusCode(200)
                .body(containsString("feature_ki"))
                .body(containsString("\"'=HYPERLINK"));
    }

    @Test
    void akzeptiertPostMitOriginHeaderDerEigenenSeite() {
        // Browser schicken bei POST immer einen Origin-Header mit. Das darf nicht zu 403 führen.
        given().contentType(ContentType.JSON).header("X-Forwarded-For", "10.0.0.4")
                .header("Origin", "https://umfrage-production.up.railway.app")
                .body("{\"answers\":{\"wer\":\"Ich\"},\"ratings\":{},\"summary\":\"x\"}")
                .when().post("/api/responses")
                .then().statusCode(201);
    }

    @Test
    void adminEndpunkteBrauchenToken() {
        given().when().get("/api/responses").then().statusCode(403);
        given().header("X-Admin-Token", "falsch").when().get("/api/stats").then().statusCode(403);
    }

    @Test
    void honeypotWirdStillVerworfen() {
        given().contentType(ContentType.JSON).header("X-Forwarded-For", "10.0.0.2")
                .body("{\"answers\":{},\"ratings\":{},\"summary\":\"spam\",\"website\":\"http://spam\"}")
                .when().post("/api/responses")
                .then().statusCode(202);
    }

    @Test
    void begrenztAntwortenProIp() {
        String body = "{\"answers\":{\"wer\":\"Ich\"},\"ratings\":{},\"summary\":\"x\"}";
        for (int i = 0; i < 3; i++) {
            given().contentType(ContentType.JSON).header("X-Forwarded-For", "10.0.0.3").body(body)
                    .when().post("/api/responses").then().statusCode(201);
        }
        given().contentType(ContentType.JSON).header("X-Forwarded-For", "10.0.0.3").body(body)
                .when().post("/api/responses").then().statusCode(429);
    }

    @Test
    void seiteWirdAusgeliefert() {
        given().when().get("/").then().statusCode(200).body(containsString("Was essen wir"));
    }
}
