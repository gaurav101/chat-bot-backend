-- ============================================================
-- Decision Tree Chat System - MySQL Database Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS decision_tree_chat
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE decision_tree_chat;

-- ============================================================
-- CORE: Decision Tree Structure
-- ============================================================

CREATE TABLE decision_trees (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    description   TEXT,
    version       VARCHAR(20)   NOT NULL DEFAULT '1.0',
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    language_code VARCHAR(10)   NOT NULL DEFAULT 'en',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by    VARCHAR(100),
    UNIQUE KEY uq_tree_name_version (name, version)
);

-- A node represents one step (question / message / action) in the tree
CREATE TABLE nodes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tree_id         BIGINT        NOT NULL,
    node_key        VARCHAR(100)  NOT NULL,          -- human-readable key e.g. "welcome", "ask_email"
    node_type       ENUM(
                        'MESSAGE',       -- plain text reply
                        'QUESTION',      -- expects user input / choice
                        'ACTION',        -- triggers backend logic
                        'NLP',           -- hand-off to NLP engine
                        'END'            -- terminates session
                    ) NOT NULL DEFAULT 'MESSAGE',
    message_text    TEXT          NOT NULL,
    metadata        JSON,                            -- extra config (buttons, media, etc.)
    is_root         BOOLEAN       NOT NULL DEFAULT FALSE,
    is_fallback     BOOLEAN       NOT NULL DEFAULT FALSE, -- used when no edge matches
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_tree_node (tree_id, node_key),
    CONSTRAINT fk_node_tree FOREIGN KEY (tree_id) REFERENCES decision_trees(id) ON DELETE CASCADE
);

-- Edges define transitions between nodes
CREATE TABLE edges (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tree_id         BIGINT        NOT NULL,
    from_node_id    BIGINT        NOT NULL,
    to_node_id      BIGINT        NOT NULL,
    match_type      ENUM(
                        'EXACT',      -- exact string match
                        'CONTAINS',   -- substring match
                        'REGEX',      -- regular expression
                        'INTENT',     -- NLP intent match
                        'ENTITY',     -- NLP entity match
                        'CONDITION',  -- SpEL expression evaluated at runtime
                        'DEFAULT'     -- catch-all / else branch
                    ) NOT NULL DEFAULT 'EXACT',
    match_value     VARCHAR(500),                   -- pattern / intent name / SpEL expression
    priority        INT           NOT NULL DEFAULT 0, -- higher = evaluated first
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_edge_tree      FOREIGN KEY (tree_id)      REFERENCES decision_trees(id) ON DELETE CASCADE,
    CONSTRAINT fk_edge_from_node FOREIGN KEY (from_node_id) REFERENCES nodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_edge_to_node   FOREIGN KEY (to_node_id)   REFERENCES nodes(id) ON DELETE CASCADE,
    INDEX idx_edges_from (from_node_id),
    INDEX idx_edges_priority (from_node_id, priority DESC)
);

-- ============================================================
-- NLP: Intents & Entities
-- ============================================================

CREATE TABLE nlp_intents (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL UNIQUE,
    description TEXT,
    tree_id     BIGINT,                              -- optional: scope intent to a tree
    is_global   BOOLEAN       NOT NULL DEFAULT TRUE, -- available across all trees
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_intent_tree FOREIGN KEY (tree_id) REFERENCES decision_trees(id) ON DELETE SET NULL
);

CREATE TABLE nlp_training_phrases (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    intent_id   BIGINT        NOT NULL,
    phrase      VARCHAR(500)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_phrase_intent FOREIGN KEY (intent_id) REFERENCES nlp_intents(id) ON DELETE CASCADE,
    INDEX idx_phrases_intent (intent_id)
);

CREATE TABLE nlp_entities (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL UNIQUE,
    entity_type ENUM('SYSTEM', 'CUSTOM') NOT NULL DEFAULT 'CUSTOM',
    description TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE nlp_entity_values (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id   BIGINT        NOT NULL,
    value       VARCHAR(200)  NOT NULL,
    synonyms    JSON,                                -- ["alias1","alias2"]
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ev_entity FOREIGN KEY (entity_id) REFERENCES nlp_entities(id) ON DELETE CASCADE,
    INDEX idx_ev_entity (entity_id)
);

-- ============================================================
-- SESSIONS & CONVERSATIONS
-- ============================================================

CREATE TABLE sessions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token   VARCHAR(128)  NOT NULL UNIQUE,
    tree_id         BIGINT        NOT NULL,
    current_node_id BIGINT,
    channel         VARCHAR(50)   NOT NULL DEFAULT 'WEB',  -- WEB, WHATSAPP, TELEGRAM, API
    user_identifier VARCHAR(200),                          -- external user id / phone / email
    context_data    JSON,                                  -- arbitrary session context
    state           ENUM('ACTIVE','COMPLETED','EXPIRED','ESCALATED') NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP,
    CONSTRAINT fk_session_tree FOREIGN KEY (tree_id)         REFERENCES decision_trees(id),
    CONSTRAINT fk_session_node FOREIGN KEY (current_node_id) REFERENCES nodes(id) ON DELETE SET NULL,
    INDEX idx_session_token (session_token),
    INDEX idx_session_user  (user_identifier),
    INDEX idx_session_state (state)
);

CREATE TABLE conversation_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT        NOT NULL,
    direction       ENUM('INBOUND','OUTBOUND') NOT NULL,
    message_text    TEXT          NOT NULL,
    node_id         BIGINT,                                -- node that produced/consumed this message
    intent_detected VARCHAR(100),
    confidence      DECIMAL(5,4),                          -- 0.0000 – 1.0000
    entities_json   JSON,                                  -- detected entities
    raw_nlp_response JSON,                                 -- full NLP provider payload
    sent_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_node    FOREIGN KEY (node_id)    REFERENCES nodes(id) ON DELETE SET NULL,
    INDEX idx_msg_session (session_id),
    INDEX idx_msg_sent_at (sent_at)
);

-- ============================================================
-- ACTIONS: Backend hooks attached to ACTION nodes
-- ============================================================

CREATE TABLE node_actions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id         BIGINT        NOT NULL UNIQUE,
    action_type     ENUM(
                        'HTTP_CALL',    -- call external REST API
                        'DB_QUERY',     -- run a named query
                        'SEND_EMAIL',   -- trigger email
                        'SEND_SMS',     -- trigger SMS
                        'CUSTOM_BEAN'   -- invoke a Spring bean
                    ) NOT NULL,
    config_json     JSON          NOT NULL,              -- action-specific config
    on_success_node BIGINT,                              -- next node if action succeeds
    on_failure_node BIGINT,                              -- next node if action fails
    timeout_ms      INT           NOT NULL DEFAULT 5000,
    retry_count     INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_action_node    FOREIGN KEY (node_id)         REFERENCES nodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_action_success FOREIGN KEY (on_success_node) REFERENCES nodes(id) ON DELETE SET NULL,
    CONSTRAINT fk_action_failure FOREIGN KEY (on_failure_node) REFERENCES nodes(id) ON DELETE SET NULL
);

-- ============================================================
-- ANALYTICS & AUDIT
-- ============================================================

CREATE TABLE analytics_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT        NOT NULL,
    event_type  VARCHAR(100)  NOT NULL,    -- NODE_VISITED, INTENT_MATCHED, ACTION_EXECUTED, etc.
    node_id     BIGINT,
    payload     JSON,
    occurred_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    INDEX idx_event_type    (event_type),
    INDEX idx_event_session (session_id),
    INDEX idx_event_time    (occurred_at)
);

CREATE TABLE audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50)   NOT NULL,  -- 'NODE', 'EDGE', 'TREE', etc.
    entity_id   BIGINT        NOT NULL,
    action      VARCHAR(50)   NOT NULL,  -- CREATE, UPDATE, DELETE
    changed_by  VARCHAR(100),
    old_value   JSON,
    new_value   JSON,
    changed_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_time   (changed_at)
);

-- ============================================================
-- SEED: Minimal sample tree
-- ============================================================

INSERT INTO decision_trees (name, description, version, language_code)
VALUES ('Customer Support', 'Default customer support decision tree', '1.0', 'en');

SET @tree_id = LAST_INSERT_ID();

INSERT INTO nodes (tree_id, node_key, node_type, message_text, is_root) VALUES
(@tree_id, 'welcome',        'QUESTION', 'Hello! How can I help you today?\n1. Billing\n2. Technical Support\n3. General Enquiry', TRUE),
(@tree_id, 'billing',        'QUESTION', 'I can help with billing. What do you need?\n1. View invoice\n2. Update payment method\n3. Cancel subscription', FALSE),
(@tree_id, 'tech_support',   'NLP',      'Please describe your issue and I will try to help.', FALSE),
(@tree_id, 'general',        'MESSAGE',  'For general enquiries please email support@example.com.', FALSE),
(@tree_id, 'end_session',    'END',      'Thank you for contacting us. Goodbye!', FALSE),
(@tree_id, 'fallback',       'QUESTION', 'I did not understand that. Could you rephrase? Or type "menu" to start over.', FALSE);

UPDATE nodes SET is_fallback = TRUE WHERE tree_id = @tree_id AND node_key = 'fallback';

-- Store node ids
SET @n_welcome    = (SELECT id FROM nodes WHERE tree_id = @tree_id AND node_key = 'welcome');
SET @n_billing    = (SELECT id FROM nodes WHERE tree_id = @tree_id AND node_key = 'billing');
SET @n_tech       = (SELECT id FROM nodes WHERE tree_id = @tree_id AND node_key = 'tech_support');
SET @n_general    = (SELECT id FROM nodes WHERE tree_id = @tree_id AND node_key = 'general');
SET @n_end        = (SELECT id FROM nodes WHERE tree_id = @tree_id AND node_key = 'end_session');
SET @n_fallback   = (SELECT id FROM nodes WHERE tree_id = @tree_id AND node_key = 'fallback');

INSERT INTO edges (tree_id, from_node_id, to_node_id, match_type, match_value, priority) VALUES
(@tree_id, @n_welcome, @n_billing,  'EXACT',   '1',                  10),
(@tree_id, @n_welcome, @n_billing,  'INTENT',  'billing_intent',     9),
(@tree_id, @n_welcome, @n_tech,     'EXACT',   '2',                  10),
(@tree_id, @n_welcome, @n_tech,     'INTENT',  'tech_support_intent',9),
(@tree_id, @n_welcome, @n_general,  'EXACT',   '3',                  10),
(@tree_id, @n_welcome, @n_general,  'INTENT',  'general_intent',     9),
(@tree_id, @n_welcome, @n_fallback, 'DEFAULT', NULL,                  0),
(@tree_id, @n_billing, @n_end,      'INTENT',  'done_intent',        10),
(@tree_id, @n_billing, @n_fallback, 'DEFAULT', NULL,                  0),
(@tree_id, @n_tech,    @n_end,      'INTENT',  'done_intent',        10),
(@tree_id, @n_tech,    @n_fallback, 'DEFAULT', NULL,                  0),
(@tree_id, @n_general, @n_end,      'DEFAULT', NULL,                  0),
(@tree_id, @n_fallback,@n_welcome,  'EXACT',   'menu',               10),
(@tree_id, @n_fallback,@n_fallback, 'DEFAULT', NULL,                  0);

INSERT INTO nlp_intents (name, description, is_global) VALUES
('billing_intent',      'User wants help with billing',        TRUE),
('tech_support_intent', 'User needs technical assistance',     TRUE),
('general_intent',      'General enquiry or information',      TRUE),
('done_intent',         'User says goodbye or is done',        TRUE),
('greeting_intent',     'User sends a greeting',               TRUE);

INSERT INTO nlp_training_phrases (intent_id, phrase) VALUES
((SELECT id FROM nlp_intents WHERE name='billing_intent'), 'I have a billing question'),
((SELECT id FROM nlp_intents WHERE name='billing_intent'), 'invoice problem'),
((SELECT id FROM nlp_intents WHERE name='billing_intent'), 'charge on my account'),
((SELECT id FROM nlp_intents WHERE name='tech_support_intent'), 'my app is broken'),
((SELECT id FROM nlp_intents WHERE name='tech_support_intent'), 'not working'),
((SELECT id FROM nlp_intents WHERE name='tech_support_intent'), 'technical issue'),
((SELECT id FROM nlp_intents WHERE name='done_intent'), 'bye'),
((SELECT id FROM nlp_intents WHERE name='done_intent'), 'thank you goodbye'),
((SELECT id FROM nlp_intents WHERE name='done_intent'), 'that is all');
