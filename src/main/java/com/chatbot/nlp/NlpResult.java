package com.chatbot.nlp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NlpResult {
    private String                 topIntent;
    private double                 confidence;
    private Map<String, Double>    allIntents;       // intent → score
    private Map<String, String>    entities;         // entity name → value
    private String                 sanitizedInput;
    private Map<String, Object>    rawProviderResponse;
}
