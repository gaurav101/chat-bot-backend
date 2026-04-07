# Decision Tree Chat Backend

A production-ready, fully extensible Java/Spring Boot chatbot backend with:
- **Decision Tree routing** with 7 edge match types
- **Pluggable NLP** (OpenNLP · Rasa · Dialogflow · OpenAI)
- **Pluggable Actions** (HTTP · Email · SMS · Custom Spring beans)
- **MySQL** persistence with session history & analytics
- **SpEL** conditions on edges for dynamic routing

---

## Project Structure

```
decision-tree-chat/
├── schema.sql                          ← Complete MySQL DDL + seed data
├── pom.xml                             ← Maven (Spring Boot 3.2, Java 17)
└── src/main/
    ├── resources/
    │   └── application.yml             ← All config (DB, NLP, session TTL)
    └── java/com/chatbot/
        ├── DecisionTreeChatApplication.java
        ├── model/Models.java           ← JPA entities
        ├── repository/Repositories.java
        ├── nlp/NlpProviders.java       ← NLP SPI + OpenNLP/Rasa/OpenAI impls
        ├── service/
        │   ├── DecisionTreeEngine.java ← Core routing logic
        │   ├── ChatService.java        ← Orchestration layer
        │   └── action/ActionHandlers.java ← Action SPI + HTTP/Email/Bean impls
        └── controller/ChatController.java ← REST API
```

---

## Database Schema — Key Tables

| Table | Purpose |
|---|---|
| `decision_trees` | Tree definitions (versioned, multi-language) |
| `nodes` | Each step: MESSAGE · QUESTION · ACTION · NLP · END |
| `edges` | Transitions with 7 match types + priority |
| `node_actions` | Backend hooks (HTTP · Email · CUSTOM_BEAN) |
| `sessions` | User sessions with context JSON |
| `conversation_messages` | Full chat history with NLP metadata |
| `nlp_intents` | Intent catalogue |
| `nlp_training_phrases` | Training data for OpenNLP fine-tuning |
| `analytics_events` | Audit trail for dashboards |

---

## REST API

### Start a Session
```http
POST /api/chat/session
Content-Type: application/json

{ "treeId": 1, "channel": "WEB", "userIdentifier": "user@example.com" }
```
**Response** → `{ sessionToken, message, nodeKey, nodeType, sessionState }`

### Send a Message
```http
POST /api/chat/message
Content-Type: application/json

{ "sessionToken": "<token>", "message": "I have a billing problem" }
```

### Get History
```http
GET /api/chat/session/{token}/history
```

---

## Edge Match Types (Priority Order)

| Type | How it matches |
|---|---|
| `EXACT` | Case-insensitive full string equality |
| `CONTAINS` | Case-insensitive substring |
| `REGEX` | Java `Pattern` match |
| `INTENT` | NLP top intent equals `matchValue` |
| `ENTITY` | NLP entities map contains key `matchValue` |
| `CONDITION` | SpEL expression: `#intent`, `#confidence`, `#entities`, `#ctx` |
| `DEFAULT` | Always matches — use as catch-all / else |

### SpEL Condition Example
```sql
-- Edge fires if NLP confidence > 80% AND session has been verified
INSERT INTO edges (match_type, match_value) VALUES
  ('CONDITION', '#confidence > 0.8 && #ctx[''verified''] == true');
```

---

## Adding a New NLP Provider

1. Implement `NlpProvider`:
```java
@Component
@ConditionalOnProperty(name = "chatbot.nlp.provider", havingValue = "myprovider")
public class MyNlpProvider implements NlpProvider {
    @Override
    public NlpResult analyse(String text, String lang, Map<String,Object> ctx) {
        // call your NLP API
        return NlpResult.builder()
            .topIntent("detected_intent")
            .confidence(0.92)
            .entities(Map.of("city", "Mumbai"))
            .build();
    }
    @Override public String providerName() { return "myprovider"; }
}
```
2. Set `chatbot.nlp.provider: myprovider` in `application.yml`.  Done.

---

## Adding a New Action Type

1. Add value to `node_actions.action_type` ENUM in MySQL.
2. Add constant to `NodeAction.ActionType` enum in Java.
3. Implement `ActionHandler`:
```java
@Component
public class MySmsHandler implements ActionHandler {
    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.SEND_SMS;
    }
    @Override
    public Map<String,Object> execute(Map<String,Object> config, Session session) {
        String to = (String) config.get("to");
        // send SMS via Twilio / Exotel / etc.
        return Map.of("smsSent", true);
    }
}
```
Spring auto-discovers it via `List<ActionHandler>` injection.

---

## Quick Start

```bash
# 1. Create DB
mysql -u root -p < schema.sql

# 2. Set env vars
export DB_USERNAME=root
export DB_PASSWORD=yourpassword

# 3. Run
mvn spring-boot:run

# 4. Test — start session
curl -X POST http://localhost:8080/api/chat/session \
  -H 'Content-Type: application/json' \
  -d '{"treeId":1,"channel":"WEB"}'

# 5. Test — send message
curl -X POST http://localhost:8080/api/chat/message \
  -H 'Content-Type: application/json' \
  -d '{"sessionToken":"<token-from-step-4>","message":"billing"}'
```

---

## Switching NLP Providers

| Provider | Config value | Additional setup |
|---|---|---|
| Apache OpenNLP | `opennlp` | Place `en-doccat.bin` in `src/main/resources/nlp-models/` |
| Rasa NLU | `rasa` | Set `chatbot.nlp.rasa.base-url` |
| Dialogflow CX | `dialogflow` | Set project ID + credentials JSON path |
| OpenAI GPT | `openai` | Set `OPENAI_API_KEY` env var |
