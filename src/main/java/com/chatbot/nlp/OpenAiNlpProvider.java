package com.chatbot.nlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Provider (activated when nlp.provider=openai)
 * Uses function-calling to extract intent + entities as JSON.
 */
@Component
@ConditionalOnProperty(name = "chatbot.nlp.provider", havingValue = "openai")
public class OpenAiNlpProvider implements NlpProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiNlpProvider.class);
    private final WebClient webClient;

    @Value("${chatbot.nlp.openai.model:gpt-4o-mini}") private String model;
    @Value("${chatbot.nlp.confidence-threshold:0.65}") private double threshold;

    public OpenAiNlpProvider(@Value("${chatbot.nlp.openai.api-key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public NlpResult analyse(String text, String languageCode, Map<String, Object> ctx) {
        String systemPrompt = """
            You are an intent classifier. Given the user message, respond ONLY with valid JSON:
            {
              "intent": "<intent_name or null>",
              "confidence": <0.0 to 1.0>,
              "entities": { "<entity>": "<value>" }
            }
            Known intents: billing_intent, tech_support_intent, general_intent, done_intent, greeting_intent.
            If none match, set intent to null and confidence to 0.0.
            """;
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system",  "content", systemPrompt),
                            Map.of("role", "user",    "content", text)
                    ),
                    "max_tokens", 200,
                    "temperature", 0
            );
            Map<String, Object> resp = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            // Parse JSON from model response
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> result = mapper.readValue(content, Map.class);

            String intent = (String) result.get("intent");
            double conf   = result.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
            Map<String, String> entities = (Map<String, String>) result.getOrDefault("entities", Map.of());

            return NlpResult.builder()
                    .topIntent(conf >= threshold ? intent : null)
                    .confidence(conf)
                    .entities(entities)
                    .sanitizedInput(text)
                    .rawProviderResponse(resp)
                    .build();
        } catch (Exception e) {
            log.error("OpenAI NLP call failed", e);
            return NlpResult.builder().topIntent(null).confidence(0.0)
                    .entities(Map.of()).sanitizedInput(text).build();
        }
    }

    @Override public String providerName() { return "openai"; }
}
