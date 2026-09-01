package com.sasimilkservice.notification.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void substitutes_placeholders_from_data() {
        Map<String, Object> data = new HashMap<>();
        data.put("producerName", "Ravi");
        data.put("litres", new BigDecimal("9.00"));
        data.put("amount", new BigDecimal("298.13"));
        data.put("collectionNumber", "MC-20260901-E-000005");

        String out = renderer.render(
                "Milk for {{producerName}}: {{litres}} L, Rs.{{amount}}, No {{collectionNumber}}.", data);

        assertThat(out).isEqualTo("Milk for Ravi: 9.00 L, Rs.298.13, No MC-20260901-E-000005.");
    }

    @Test
    void missing_and_null_keys_render_empty() {
        assertThat(renderer.render("a={{a}} b={{ b }} c={{c}}", Map.of("a", "1")))
                .isEqualTo("a=1 b= c=");
    }

    @Test
    void null_template_is_empty_string() {
        assertThat(renderer.render(null, Map.of())).isEmpty();
    }
}
