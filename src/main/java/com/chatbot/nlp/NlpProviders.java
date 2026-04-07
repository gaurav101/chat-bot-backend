package com.chatbot.nlp;

import lombok.*;
import org.apache.opennlp.tools.doccat.*;
import org.apache.opennlp.tools.tokenize.SimpleTokenizer;
import org.apache.opennlp.tools.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.*;

// ════════════════════════════════════════════════════════════════
// NLP Result DTO
// ════════════════════════════════════════════════════════════════
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NlpResult {
    private String                 topIntent;
    private double                 confidence;
    private Map<String, Double>    allIntents;       // intent → score
    private Map<String, String>    entities;         // entity name → value
    private String                 sanitizedInput;
    private Map<String, Object>    rawProviderResponse;
}

// ════════════════════════════════════════════════════════════════
// NLP Provider SPI — implement this to add any new provider
// ════════════════════════════════════════════════════════════════
public interface NlpProvider {
    /**
     * Analyse user text and return structured NLP result.
     * Implementations must be stateless and thread-safe.
     */
    NlpResult analyse(String text, String languageCode, Map<String, Object> sessionContext);

    /** Human-readable name used in logs and config. */
    String providerName();
}

// ════════════════════════════════════════════════════════════════
// Apache OpenNLP Provider  (activated when nlp.provider=opennlp)
// ════════════════════════════════════════════════════════════════
@Component
@ConditionalOnProperty(name = "chatbot.nlp.provider", havingValue = "opennlp")
public class OpenNlpProvider implements NlpProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenNlpProvider.class);

    // DocumentCategorizerME trained model — loaded once at startup
    private DoccatModel         model;
    private DocumentCategorizerME categorizer;
    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;

    @Value("${chatbot.nlp.opennlp.models-path:nlp-models/}")
    private String modelsPath;

    @Value("${chatbot.nlp.confidence-threshold:0.65}")
    private double confidenceThreshold;

    // Lazy-init so context loads even without pre-trained models
    private boolean modelLoaded = false;

    public void loadModel() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(modelsPath + "en-doccat.bin")) {
            if (is == null) {
                log.warn("OpenNLP doccat model not found at {}en-doccat.bin — running in echo mode", modelsPath);
                return;
            }
            model       = new DoccatModel(is);
            categorizer = new DocumentCategorizerME(model);
            modelLoaded = true;
            log.info("OpenNLP document categorizer loaded successfully.");
        } catch (IOException e) {
            log.error("Failed to load OpenNLP model", e);
        }
    }

    @Override
    public NlpResult analyse(String text, String languageCode, Map<String, Object> ctx) {
        if (!modelLoaded) loadModel();

        String clean = sanitize(text);
        Map<String, Object> raw = new HashMap<>();

        if (!modelLoaded) {
            // No model — return empty result so DEFAULT edge fires
            return NlpResult.builder()
                    .topIntent(null).confidence(0.0)
                    .allIntents(Map.of()).entities(Map.of())
                    .sanitizedInput(clean).rawProviderResponse(raw).build();
        }

        String[] tokens  = tokenizer.tokenize(clean);
        double[] outcomes = categorizer.categorize(tokens);
        String   topCat  = categorizer.getBestCategory(outcomes);

        Map<String, Double> allScores = new LinkedHashMap<>();
        for (int i = 0; i < categorizer.getNumberOfCategories(); i++) {
            allScores.put(categorizer.getCategory(i), outcomes[i]);
        }

        raw.put("tokens", tokens);
        raw.put("scores", allScores);

        return NlpResult.builder()
                .topIntent(outcomes[categorizer.getIndex(topCat)] >= confidenceThreshold ? topCat : null)
                .confidence(outcomes[categorizer.getIndex(topCat)])
                .allIntents(allScores)
                .entities(extractBasicEntities(clean))
                .sanitizedInput(clean)
                .rawProviderResponse(raw)
                .build();
    }

    private String sanitize(String input) {
        return input == null ? "" : input.trim().toLowerCase();
    }

    /** Lightweight regex-based entity extraction (email, phone, number). */
    private Map<String, String> extractBasicEntities(String text) {
        Map<String, String> entities = new LinkedHashMap<>();
        // Email
        var emailMatcher = java.util.regex.Pattern
                .compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
                .matcher(text);
        if (emailMatcher.find()) entities.put("email", emailMatcher.group());

        // Phone (international-ish)
        var phoneMatcher = java.util.regex.Pattern
                .compile("\\+?[0-9][0-9\\s\\-]{7,14}[0-9]")
                .matcher(text);
        if (phoneMatcher.find()) entities.put("phone", phoneMatcher.group().replaceAll("\\s", ""));

        // Number
        var numMatcher = java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(text);
        if (numMatcher.find()) entities.put("number", numMatcher.group());

        return entities;
    }

    @Override public String providerName() { return "opennlp"; }
}

// ════════════════════════════════════════════════════════════════
// Rasa NLU Provider  (activated when nlp.provider=rasa)
// ════════════════════════════════════════════════════════════════
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

// ════════════════════════════════════════════════════════════════
// OpenAI Provider  (activated when nlp.provider=openai)
// Uses function-calling to extract intent + entities as JSON.
// ════════════════════════════════════════════════════════════════
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
