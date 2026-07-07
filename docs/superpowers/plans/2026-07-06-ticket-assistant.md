# Intelligent Railway Ticket Assistant Implementation Plan

> **For agentic workers:** Use `subagent-driven-development` for independent tasks, or `executing-plans` for tightly coupled tasks. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the project practice part of the Yuque document: an intelligent railway ticket assistant that supports sessions, streaming chat, persistent memory, RAG, booking, refunding, and weather tool calls.

**Architecture:** A Spring Boot 3.4.0 application serves both REST/SSE APIs and a static HTML frontend. MyBatis-Plus stores sessions, chat messages, ticket orders, and refund records in MySQL; the test profile can use H2. AI replies use DeepSeek through its OpenAI-compatible streaming API when configured, with a local demonstration assistant as a runnable fallback.

**Tech Stack:** Java 17, Spring Boot 3.4.0, MyBatis-Plus, MySQL, LangChain4j tool annotations, Reactor Flux streaming, static HTML/CSS/JavaScript.

## Global Constraints

- Only implement the project practice and acceptance requirements, not the first five teaching chapters.
- Server port must be `19999`.
- Runtime database target is MySQL database `ticket_assistant`.
- Keep the frontend in `src/main/resources/static/index.html`.
- Keep the knowledge base in `src/main/resources/rag/rag-service.txt`.
- DeepSeek API key must be configured by environment variable and must not be committed.

---

### Task 1: Project Skeleton

**Files:**
- Create: `pom.xml`
- Create: `run-dev.ps1`
- Create: `src/main/java/org/gecedu/ticketassistant/TicketAssistantApplication.java`
- Create: `src/main/resources/application.yaml`
- Create: `README.md`

**Interfaces:**
- Produces a Spring Boot app listening on `19999`.
- Produces a simple local build runner for machines without global Maven.

- [x] Create Maven project with Spring Boot 3.4.0 and Java 17.
- [x] Add WebFlux, Validation, MyBatis-Plus, MySQL, LangChain4j, and test dependencies.
- [x] Configure MySQL through environment variables.

### Task 2: Database and CRUD APIs

**Files:**
- Create: `src/main/resources/db/schema.sql`
- Create: `src/main/java/org/gecedu/ticketassistant/session/*`
- Create: `src/main/java/org/gecedu/ticketassistant/chat/*`
- Create: `src/main/java/org/gecedu/ticketassistant/order/*`

**Interfaces:**
- `GET /api/session/list`
- `POST /api/session`
- `PUT /api/session/{id}`
- `DELETE /api/session/{id}`
- `GET /api/chat/history/{sessionId}`
- `GET /api/order/list`

- [ ] Implement session list/create/update/delete.
- [ ] Implement chat history persistence by session.
- [ ] Implement order and refund persistence.

### Task 3: AI, RAG, and Tools

**Files:**
- Create: `src/main/java/org/gecedu/ticketassistant/ai/*`
- Create: `src/main/java/org/gecedu/ticketassistant/tool/*`
- Create: `src/main/resources/rag/rag-service.txt`

**Interfaces:**
- `GET /api/chat/stream?sessionId={id}&message={message}`
- Tool methods: `bookTicket`, `refundTicket`, `queryWeather`.

- [ ] Implement DeepSeek streaming client.
- [ ] Implement local fallback assistant for deterministic demos.
- [ ] Implement RAG text loading and prompt context.
- [ ] Implement booking, refund, and weather tools.

### Task 4: Frontend

**Files:**
- Create: `src/main/resources/static/index.html`

**Interfaces:**
- Browser entry: `http://127.0.0.1:19999/index.html`

- [ ] Build a single-page chat UI.
- [ ] Support session list, create, rename, delete.
- [ ] Support streaming output and history restore.
- [ ] Support order list display.

### Task 5: Verification

**Files:**
- Create: `src/test/java/org/gecedu/ticketassistant/ai/IntentParserTest.java`
- Create: `src/test/java/org/gecedu/ticketassistant/tool/TicketToolServiceTest.java`

**Commands:**
- `powershell -ExecutionPolicy Bypass -File .\run-dev.ps1 test`
- `powershell -ExecutionPolicy Bypass -File .\run-dev.ps1 run`

- [ ] Run tests where Java/Maven are available.
- [ ] Verify the frontend loads at `/index.html`.
- [ ] Verify acceptance items are mapped in `README.md`.
