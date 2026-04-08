package com.chatbot.nlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rasa NLU Provider (activated when nlp.provider=rasa)
 */
@Component
@ConditionalOnProperty(name = "chatbot.nlp.provider", havingValue = "rasa")
public class RasaProvider implements NlpProvider {

    private static final Logger log = LoggerFactory.getLogger(RasaProvider.class);
    private final WebClient webClient;

    @Value("${chatbot.nlp.confidence-threshold:0.65}") private double threshold;

    public RasaProvider(@Value("${chatbot.nlp.rasa.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public NlpResult analyse(String text, String languageCode, Map<String, Object> ctx) {
        try {
            Map<String, Object> body = Map.of("text", text);
            Map<String, Object> response = webClient.post()
                    .uri("/model/parse")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return emptyResult(text);

            Map<String, Object> intentMap = (Map<String, Object>) response.get("intent");
            String  name  = intentMap != null ? (String) intentMap.get("name")       : null;
            double  conf  = intentMap != null ? ((Number) intentMap.get("confidence")).doubleValue() : 0.0;

            // Entities
            List<Map<String, Object>> entityList =
                    (List<Map<String, Object>>) response.getOrDefault("entities", List.of());
            Map<String, String> entities = new LinkedHashMap<>();
            for (var e : entityList)
                entities.put((String) e.get("entity"), String.valueOf(e.get("value")));

            return NlpResult.builder()
                    .topIntent(conf >= threshold ? name : null)
                    .confidence(conf)
                    .entities(entities)
                    .sanitizedInput(text)
                    .rawProviderResponse(response)
                    .build();
        } catch (Exception ex) {
            log.error("Rasa NLU call failed", ex);
            return emptyResult(text);
        }
    }

    private NlpResult emptyResult(String text) {
        return NlpResult.builder().topIntent(null).confidence(0.0)
                .entities(Map.of()).sanitizedInput(text).build();
    }

    @Override public String providerName() { return "rasa"; }
}
