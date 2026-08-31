package com.sasimilkservice.collection;

import com.sasimilkservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: login → configure rate → add producer → record a collection →
 * verify the formula-priced breakdown and idempotent replay.
 */
class CollectionApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private HttpHeaders authHeaders() {
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "admin123"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth((String) login.getBody().get("accessToken"));
        return headers;
    }

    private <T> ResponseEntity<T> post(String url, Object body, HttpHeaders headers, Class<T> type) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    @Test
    void records_a_collection_priced_by_the_ts_value_formula_and_is_idempotent() {
        HttpHeaders headers = authHeaders();

        // COMMON rate coefficient
        post("/api/v1/rates",
                Map.of("scope", "COMMON", "tsValue", 265.0, "effectiveFrom", "2026-01-01"),
                headers, Map.class);

        // producer in region 1
        ResponseEntity<Map> producer = post("/api/v1/producers",
                Map.of("producerName", "IT Producer", "mobileNumber", "9876500000", "regionId", 1),
                headers, Map.class);
        assertThat(producer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String producerNumber = (String) producer.getBody().get("producerNumber");

        UUID clientTxn = UUID.randomUUID();
        Map<String, Object> collection = Map.of(
                "clientTransactionId", clientTxn.toString(),
                "producerReference", producerNumber,
                "collectionDate", "2026-08-31",
                "shift", "MORNING",
                "litres", 10.0,
                "fat", 4.1,
                "snf", 8.6);

        ResponseEntity<Map> first = post("/api/v1/collections", collection, headers, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = first.getBody();
        assertThat((Boolean) body.get("duplicate")).isFalse();

        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) body.get("breakdown");
        assertThat(new java.math.BigDecimal(breakdown.get("ratePerLitre").toString()))
                .isEqualByComparingTo("33.6550");   // (4.1 + 8.6) * 265 / 100
        assertThat(new java.math.BigDecimal(breakdown.get("netAmount").toString()))
                .isEqualByComparingTo("336.55");     // 10 * 33.6550

        // replay with the same clientTransactionId -> 200 + duplicate, no new row
        ResponseEntity<Map> replay = post("/api/v1/collections", collection, headers, Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Boolean) replay.getBody().get("duplicate")).isTrue();
        assertThat(replay.getBody().get("id")).isEqualTo(body.get("id"));

        ResponseEntity<Map> list = rest.exchange(
                "/api/v1/collections?producerId=" + producer.getBody().get("id"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(((Number) list.getBody().get("totalElements")).intValue()).isEqualTo(1);
    }
}
