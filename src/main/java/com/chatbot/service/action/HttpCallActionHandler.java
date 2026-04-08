package com.chatbot.service.action;

import com.chatbot.model.NodeAction;
import com.chatbot.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

// ════════════════════════════════════════════════════════════════
// HTTP_CALL handler
// config keys: url, method (GET/POST), headers (map), bodyTemplate (SpEL)
// ════════════════════════════════════════════════════════════════
@Component
@Slf4j
@SuppressWarnings("unchecked")
public class HttpCallActionHandler implements ActionHandler {

    private final WebClient.Builder webClientBuilder;

    public HttpCallActionHandler(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.HTTP_CALL;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception {
        String url    = (String) config.get("url");
        String method = ((String) config.getOrDefault("method", "GET")).toUpperCase();

        WebClient client = webClientBuilder.baseUrl(url).build();
        Map<String, Object> responseBody;

        if ("POST".equals(method)) {
            Map<String, Object> body = (Map<String, Object>) config.getOrDefault("body", Map.of());
            responseBody = client.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } else {
            responseBody = client.get()
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("httpResponse", responseBody);
        return result;
    }
}
