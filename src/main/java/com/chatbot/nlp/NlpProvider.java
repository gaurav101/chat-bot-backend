package com.chatbot.nlp;

import java.util.Map;

/**
 * NLP Provider SPI — implement this to add any new provider
 */
public interface NlpProvider {
    /**
     * Analyse user text and return structured NLP result.
     * Implementations must be stateless and thread-safe.
     */
    NlpResult analyse(String text, String languageCode, Map<String, Object> sessionContext);

    /** Human-readable name used in logs and config. */
    String providerName();
}
