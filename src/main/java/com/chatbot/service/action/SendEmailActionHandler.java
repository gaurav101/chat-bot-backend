package com.chatbot.service.action;

import com.chatbot.model.NodeAction;
import com.chatbot.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

// ════════════════════════════════════════════════════════════════
// EMAIL handler (stub — wire to JavaMailSender / SendGrid etc.)
// config keys: to, subject, templateName
// ════════════════════════════════════════════════════════════════
@Component
@Slf4j
public class SendEmailActionHandler implements ActionHandler {

    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.SEND_EMAIL;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception {
        String to      = (String) config.get("to");
        String subject = (String) config.get("subject");
        // TODO: inject JavaMailSender / Spring Mail / SendGrid SDK here
        log.info("STUB: sending email to={} subject={}", to, subject);
        return Map.of("emailSent", true, "emailTo", to);
    }
}
