package com.chatbot.nlp;

import org.apache.opennlp.tools.doccat.DoccatModel;
import org.apache.opennlp.tools.doccat.DocumentCategorizerME;
import org.apache.opennlp.tools.tokenize.SimpleTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apache OpenNLP Provider (activated when nlp.provider=opennlp)
 */
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
