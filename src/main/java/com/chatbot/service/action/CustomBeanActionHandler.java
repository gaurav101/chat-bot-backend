package com.chatbot.service.action;

import com.chatbot.model.NodeAction;
import com.chatbot.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

// ════════════════════════════════════════════════════════════════
// CUSTOM_BEAN handler — delegates to a named Spring bean
// config keys: beanName (required)
// ════════════════════════════════════════════════════════════════
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomBeanActionHandler implements ActionHandler {

    private final org.springframework.context.ApplicationContext appCtx;

    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.CUSTOM_BEAN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception {
        String beanName = (String) config.get("beanName");
        if (beanName == null) throw new IllegalArgumentException("beanName is required for CUSTOM_BEAN action");

        // The named bean must implement ActionHandler (the execute method)
        ActionHandler bean = appCtx.getBean(beanName, ActionHandler.class);
        return bean.execute(config, session);
    }
}
