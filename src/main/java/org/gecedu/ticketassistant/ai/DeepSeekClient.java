package org.gecedu.ticketassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gecedu.ticketassistant.chat.ChatMessage;
import org.gecedu.ticketassistant.tool.TicketToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TicketToolService ticketToolService;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public DeepSeekClient(
            ObjectMapper objectMapper,
            KnowledgeBaseService knowledgeBaseService,
            TicketToolService ticketToolService,
            @Value("${ai.deepseek.api-key:}") String apiKey,
            @Value("${ai.deepseek.base-url}") String baseUrl,
            @Value("${ai.deepseek.model}") String model,
            @Value("${ai.deepseek.timeout-seconds:20}") long timeoutSeconds
    ) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = objectMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ticketToolService = ticketToolService;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Flux<String> stream(String message, List<ChatMessage> history) {
        List<Map<String, String>> messages = buildMessages(message, history);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("messages", messages);
        body.put("tools", buildToolDefinitions());
        body.put("tool_choice", "auto");

        return webClient.post()
                .uri(baseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout)
                .collectList()
                .flatMapMany(chunks -> processStreamResponse(chunks, messages));
    }

    private Flux<String> processStreamResponse(List<String> chunks, List<Map<String, String>> messages) {
        StringBuilder text = new StringBuilder();
        String toolCallId = null;
        String toolName = null;
        StringBuilder toolArgs = new StringBuilder();

        for (String chunk : chunks) {
            for (String line : chunk.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("data:")) {
                    trimmed = trimmed.substring(5).trim();
                }
                if ("[DONE]".equals(trimmed) || trimmed.isEmpty()) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(trimmed);
                    JsonNode choices = node.path("choices");
                    if (choices.isEmpty()) {
                        continue;
                    }
                    JsonNode delta = choices.path(0).path("delta");

                    JsonNode content = delta.path("content");
                    if (!content.isMissingNode() && !content.asText().isEmpty()) {
                        text.append(content.asText());
                    }

                    JsonNode toolCalls = delta.path("tool_calls");
                    if (!toolCalls.isMissingNode() && toolCalls.isArray() && !toolCalls.isEmpty()) {
                        JsonNode firstTc = toolCalls.get(0);
                        String id = firstTc.path("id").asText(null);
                        if (id != null && !id.isEmpty()) {
                            toolCallId = id;
                        }
                        JsonNode fn = firstTc.path("function");
                        String name = fn.path("name").asText(null);
                        if (name != null && !name.isEmpty()) {
                            toolName = name;
                        }
                        String args = fn.path("arguments").asText(null);
                        if (args != null && !args.isEmpty()) {
                            toolArgs.append(args);
                        }
                    }

                    String finishReason = choices.path(0).path("finish_reason").asText(null);
                    if ("tool_calls".equals(finishReason) && toolName != null) {
                        return handleToolCall(messages, toolCallId, toolName, toolArgs.toString());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (text.isEmpty()) {
            text.append("抱歉，我暂时无法处理您的请求，请稍后再试。");
        }
        return charStream(text.toString());
    }

    private Flux<String> handleToolCall(List<Map<String, String>> messages, String callId, String toolName, String toolArgs) {
        log.info("DeepSeek requested tool call: {}", toolName);
        String resultText = executeToolSafe(toolName, toolArgs);

        List<Map<String, Object>> extendedMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            extendedMessages.add(new HashMap<>(msg));
        }

        Map<String, Object> assistantToolCall = new HashMap<>();
        assistantToolCall.put("role", "assistant");
        Map<String, Object> tc = new HashMap<>();
        tc.put("id", callId != null ? callId : "call_auto");
        tc.put("type", "function");
        tc.put("function", Map.of("name", toolName, "arguments", toolArgs));
        assistantToolCall.put("tool_calls", List.of(tc));
        extendedMessages.add(assistantToolCall);

        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("role", "tool");
        toolResult.put("tool_call_id", callId != null ? callId : "call_auto");
        toolResult.put("content", resultText);
        extendedMessages.add(toolResult);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("messages", extendedMessages);

        return webClient.post()
                .uri(baseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout)
                .collectList()
                .flatMapMany(chunks -> {
                    StringBuilder finalText = new StringBuilder();
                    for (String chunk : chunks) {
                        for (String line : chunk.split("\\R")) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty()) continue;
                            if (trimmed.startsWith("data:")) trimmed = trimmed.substring(5).trim();
                            if ("[DONE]".equals(trimmed) || trimmed.isEmpty()) continue;
                            try {
                                JsonNode node = objectMapper.readTree(trimmed);
                                JsonNode content = node.path("choices").path(0).path("delta").path("content");
                                if (!content.isMissingNode() && !content.asText().isEmpty()) {
                                    finalText.append(content.asText());
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (finalText.isEmpty()) {
                        finalText.append(resultText);
                    }
                    return charStream(finalText.toString());
                });
    }

    private String executeTool(String name, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            return switch (name) {
                case "bookTicket" -> ticketToolService.bookTicket(
                        args.path("passengerName").asText(),
                        args.path("idCard").asText(),
                        args.path("trainNo").asText(),
                        args.path("travelDate").asText(),
                        args.path("seatType").asText()
                ).message();
                case "refundTicket" -> ticketToolService.refundTicket(
                        args.has("orderNo") && !args.path("orderNo").asText().isBlank() ? args.path("orderNo").asText() : null,
                        args.has("passengerName") ? args.path("passengerName").asText() : null,
                        args.has("idCard") ? args.path("idCard").asText() : null
                ).message();
                case "queryWeather" -> ticketToolService.queryWeather(
                        args.path("city").asText()
                ).message();
                default -> "未知工具：" + name;
            };
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", e.getMessage());
            return "工具参数解析失败：" + e.getMessage();
        }
    }

    private String executeToolSafe(String name, String arguments) {
        try {
            return executeTool(name, arguments);
        } catch (Exception e) {
            log.warn("Tool execution failed: {}", e.getMessage());
            return "工具调用失败：" + e.getMessage();
        }
    }

    private List<Map<String, String>> buildMessages(String message, List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", """
                        您是"12345"铁路公司的客户聊天支持代理。请以友好、乐于助人且愉快的方式回复。
                        当用户需要购票、退票或查询天气时，请调用对应的工具函数完成操作。
                        购票前必须确认：乘车人姓名、身份证号、车次、乘车日期、座位类型。
                        退票前必须确认：订单号，或者乘车人姓名+身份证号。
                        如果用户提供的信息不完整，请先向用户询问缺失的信息，再调用工具。
                        如果你不确定是否该调用工具，请直接回复用户的提问。
                        以下是知识库资料：
                        """ + knowledgeBaseService.retrieve(message)
        ));

        int start = Math.max(0, history.size() - 12);
        for (ChatMessage item : history.subList(start, history.size())) {
            messages.add(Map.of("role", normalizeRole(item.getRole()), "content", item.getContent()));
        }
        messages.add(Map.of("role", "user", "content", message));
        return messages;
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> bookTicketFn = new HashMap<>();
        bookTicketFn.put("name", "bookTicket");
        bookTicketFn.put("description", "根据乘车人信息完成铁路购票，并在数据库中创建购票订单");
        bookTicketFn.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                        "passengerName", Map.of("type", "string", "description", "乘车人姓名"),
                        "idCard", Map.of("type", "string", "description", "身份证号"),
                        "trainNo", Map.of("type", "string", "description", "车次"),
                        "travelDate", Map.of("type", "string", "description", "乘车日期，格式为yyyy-MM-dd"),
                        "seatType", Map.of("type", "string", "description", "座位类型：硬座/软座/硬卧/软卧")
                ),
                "required", List.of("passengerName", "idCard", "trainNo", "travelDate", "seatType")
        ));
        tools.add(Map.of("type", "function", "function", bookTicketFn));

        Map<String, Object> refundTicketFn = new HashMap<>();
        refundTicketFn.put("name", "refundTicket");
        refundTicketFn.put("description", "根据订单号或乘车人身份信息完成铁路退票，计算手续费并保存退票记录");
        refundTicketFn.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderNo", Map.of("type", "string", "description", "订单号，可为空"),
                        "passengerName", Map.of("type", "string", "description", "乘车人姓名，可为空"),
                        "idCard", Map.of("type", "string", "description", "身份证号，可为空")
                ),
                "required", List.of()
        ));
        tools.add(Map.of("type", "function", "function", refundTicketFn));

        Map<String, Object> queryWeatherFn = new HashMap<>();
        queryWeatherFn.put("name", "queryWeather");
        queryWeatherFn.put("description", "查询指定城市的实时天气，供铁路出行前参考");
        queryWeatherFn.put("parameters", Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string", "description", "城市名称")),
                "required", List.of("city")
        ));
        tools.add(Map.of("type", "function", "function", queryWeatherFn));

        return tools;
    }

    private Flux<String> charStream(String text) {
        List<String> pieces = text.chars()
                .mapToObj(code -> String.valueOf((char) code))
                .toList();
        return Flux.fromIterable(pieces).delayElements(Duration.ofMillis(12));
    }

    private String normalizeRole(String role) {
        return "assistant".equals(role) ? "assistant" : "user";
    }
}
