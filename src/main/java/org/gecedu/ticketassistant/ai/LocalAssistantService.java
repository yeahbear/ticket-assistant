package org.gecedu.ticketassistant.ai;

import org.gecedu.ticketassistant.chat.ChatMessage;
import org.gecedu.ticketassistant.tool.TicketToolService;
import org.gecedu.ticketassistant.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LocalAssistantService {

    private static final Pattern ORDER_NO_IN_TEXT = Pattern.compile("订单号：?(TA\\d{10,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDINAL_NUMBER = Pattern.compile("第?([1-9])个?");

    private final KnowledgeBaseService knowledgeBaseService;
    private final TicketToolService ticketToolService;

    public LocalAssistantService(KnowledgeBaseService knowledgeBaseService, TicketToolService ticketToolService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.ticketToolService = ticketToolService;
    }

    public String answer(String message, List<ChatMessage> history) {
        String latestAssistant = latestAssistant(history);

        if (IntentParser.isTrainTicketSearchIntent(message)) {
            RouteInfo route = IntentParser.parseRoute(message);
            BookingInfo info = IntentParser.parseBooking(message);
            if (route == null) {
                return "请告诉我要查询哪两个城市之间的车票，例如：查询广州到上海 2026-07-15 车票。";
            }
            if (info.travelDate() == null) {
                return "请补充乘车日期，格式如 2026-07-15。";
            }
            return ticketToolService.queryTrainTickets(route.depart(), route.arrive(), info.travelDate().toString()).message();
        }

        if (IntentParser.isWeatherIntent(message)) {
            String city = IntentParser.parseWeatherCity(message);
            if (city == null || city.isBlank()) {
                return "请告诉我要查询哪个城市的天气，例如：查询广州天气。";
            }
            return ticketToolService.queryWeather(city).message();
        }

        if (IntentParser.isOrderQueryIntent(message)) {
            return ticketToolService.queryOrders().message();
        }

        if (IntentParser.isIdentityQuestion(message)) {
            return "我只能看到当前会话中的聊天记忆。" + summarizeUserHistory(history);
        }

        boolean refundTurn = IntentParser.isRefundIntent(message)
                || (isWaitingForRefundInfo(latestAssistant) && (IntentParser.hasRefundInfo(message) || IntentParser.isSelectionReply(message)));
        if (refundTurn) {
            String refundText = IntentParser.isRefundIntent(message) ? message : pendingUserText(history, "退票成功") + "\n" + message;
            return handleRefund(refundText, message, latestAssistant);
        }

        boolean bookingTurn = IntentParser.isBookingIntent(message)
                || (isWaitingForBookingInfo(latestAssistant) && IntentParser.hasBookingInfo(message));
        if (bookingTurn) {
            String bookingText = IntentParser.isBookingIntent(message) ? message : pendingUserText(history, "购票成功") + "\n" + message;
            return handleBooking(bookingText);
        }

        return "您好，我是12345铁路公司的智能票务助手。当前可为您办理“我要购票”“我要退票”和“查询天气”。\n\n知识库摘要：\n"
                + knowledgeBaseService.retrieve(message);
    }

    private String handleBooking(String combined) {
        BookingInfo info = IntentParser.parseBooking(combined);
        if (!info.complete()) {
            return "可以为您办理购票。\n\n"
                    + "订票需要提供以下信息：\n"
                    + "1. 乘车人姓名\n"
                    + "2. 身份证号\n"
                    + "3. 车次\n"
                    + "4. 乘车日期，格式如 2026-06-25\n"
                    + "5. 座位类型：商务座 / 一等座 / 二等座 / 硬座 / 软座 / 硬卧 / 软卧 / 无座\n"
                    + "如需真实余票校验，可同时提供出发地和目的地，例如：广州到上海。\n\n"
                    + "当前还缺：" + String.join("、", info.missing()) + "\n"
                    + "请一次性补充这些信息，我会为您生成购票订单。";
        }
        try {
            ToolResult result = ticketToolService.bookTicket(
                    info.passengerName(),
                    info.idCard(),
                    info.trainNo(),
                    info.travelDate().toString(),
                    info.seatType(),
                    info.depart(),
                    info.arrive()
            );
            return result.message();
        } catch (IllegalArgumentException ex) {
            return "购票暂未完成：" + ex.getMessage() + "。请核对乘车人、车次、日期和座位类型后重新提交。";
        }
    }

    private String handleRefund(String combined, String message, String latestAssistant) {
        String selectedOrderNo = parseSelectedOrderNo(message, latestAssistant);
        if (selectedOrderNo != null) {
            return refundByOrderNo(selectedOrderNo);
        }

        RefundInfo info = IntentParser.parseRefund(combined);
        if (!info.complete()) {
            return "可以为您办理退票。\n\n"
                    + "退票有两种方式：\n"
                    + "1. 提供订单号\n"
                    + "2. 提供乘车人姓名 + 身份证号\n\n"
                    + "当前还缺：" + String.join("、", info.missing()) + "\n"
                    + "请补充后我会为您办理退票。";
        }
        try {
            return ticketToolService.refundTicket(info.orderNo(), info.passengerName(), info.idCard()).message();
        } catch (IllegalArgumentException ex) {
            return "退票暂未完成：" + ex.getMessage() + "。请核对订单号或乘车人身份信息。";
        }
    }

    private String refundByOrderNo(String orderNo) {
        try {
            return ticketToolService.refundTicket(orderNo, null, null).message();
        } catch (IllegalArgumentException ex) {
            return "退票暂未完成：" + ex.getMessage() + "。请核对订单号。";
        }
    }

    private String parseSelectedOrderNo(String message, String latestAssistant) {
        if (!latestAssistant.contains("请选择要退票的订单号")) {
            return null;
        }
        int index = selectedIndex(message);
        if (index < 0) {
            return null;
        }
        Matcher matcher = ORDER_NO_IN_TEXT.matcher(latestAssistant);
        int current = 0;
        while (matcher.find()) {
            if (current == index) {
                return matcher.group(1).toUpperCase();
            }
            current++;
        }
        return null;
    }

    private int selectedIndex(String message) {
        Matcher matcher = ORDINAL_NUMBER.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) - 1;
        }
        if (message.contains("第一")) {
            return 0;
        }
        if (message.contains("第二")) {
            return 1;
        }
        if (message.contains("第三")) {
            return 2;
        }
        if (message.contains("第四")) {
            return 3;
        }
        if (message.contains("第五")) {
            return 4;
        }
        return -1;
    }

    private boolean isWaitingForBookingInfo(String latestAssistant) {
        return latestAssistant.contains("订票需要提供") || latestAssistant.contains("生成购票订单");
    }

    private boolean isWaitingForRefundInfo(String latestAssistant) {
        return latestAssistant.contains("退票有两种方式") || latestAssistant.contains("请选择要退票的订单号");
    }

    private String latestAssistant(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if ("assistant".equals(message.getRole())) {
                return message.getContent();
            }
        }
        return "";
    }

    private String summarizeUserHistory(List<ChatMessage> history) {
        List<String> names = history.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(ChatMessage::getContent)
                .map(IntentParser::parsePersonName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        if (!names.isEmpty()) {
            return "你在当前会话里提到过姓名：" + String.join("、", names) + "。";
        }
        String summary = history.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(ChatMessage::getContent)
                .limit(5)
                .collect(Collectors.joining("；"));
        return summary.isBlank() ? "当前会话里还没有明确的个人信息。" : "当前会话历史里，你之前说过：" + summary;
    }

    private String pendingUserText(List<ChatMessage> history, String successMarker) {
        int start = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage item = history.get(i);
            if ("assistant".equals(item.getRole()) && item.getContent() != null && item.getContent().contains(successMarker)) {
                start = i + 1;
                break;
            }
        }
        return history.subList(start, history.size()).stream()
                .filter(item -> "user".equals(item.getRole()))
                .map(ChatMessage::getContent)
                .collect(Collectors.joining("\n"));
    }
}
