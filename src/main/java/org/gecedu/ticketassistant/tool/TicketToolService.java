package org.gecedu.ticketassistant.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.gecedu.ticketassistant.order.BookingRequest;
import org.gecedu.ticketassistant.order.RefundRequest;
import org.gecedu.ticketassistant.order.RefundResult;
import org.gecedu.ticketassistant.order.TicketOrder;
import org.gecedu.ticketassistant.order.TicketOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.List;

@Service
public class TicketToolService {

    private static final Logger log = LoggerFactory.getLogger(TicketToolService.class);

    private final TicketOrderService ticketOrderService;
    private final QWeatherClient qWeatherClient;

    public TicketToolService(TicketOrderService ticketOrderService, QWeatherClient qWeatherClient) {
        this.ticketOrderService = ticketOrderService;
        this.qWeatherClient = qWeatherClient;
    }

    @Tool("根据乘车人信息完成铁路购票，并在数据库中创建购票订单")
    public ToolResult bookTicket(
            @P("乘车人姓名") String passengerName,
            @P("身份证号") String idCard,
            @P("车次") String trainNo,
            @P("乘车日期，格式为yyyy-MM-dd") String travelDate,
            @P("座位类型：硬座/软座/硬卧/软卧") String seatType
    ) {
        LocalDate parsedTravelDate;
        try {
            parsedTravelDate = LocalDate.parse(travelDate == null ? "" : travelDate.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("乘车日期格式应为 yyyy-MM-dd，例如 2026-08-25");
        }
        TicketOrder order = ticketOrderService.book(new BookingRequest(
                trimToEmpty(passengerName),
                trimToEmpty(idCard),
                trimToEmpty(trainNo),
                parsedTravelDate,
                trimToEmpty(seatType)
        ));
        return new ToolResult(true, "购票成功，订单号：" + order.getOrderNo()
                + "，车次：" + order.getTrainNo()
                + "，日期：" + order.getTravelDate()
                + "，座位：" + order.getSeatType() + order.getSeatNo()
                + "，票价：" + order.getPrice() + "元。");
    }

    @Tool("根据订单号或乘车人身份信息完成铁路退票，计算手续费并保存退票记录")
    public ToolResult refundTicket(
            @P("订单号，可为空") String orderNo,
            @P("乘车人姓名，可为空") String passengerName,
            @P("身份证号，可为空") String idCard
    ) {
        RefundResult result = ticketOrderService.refund(new RefundRequest(blankToNull(orderNo), blankToNull(passengerName), blankToNull(idCard)));
        return new ToolResult(true, "退票成功，订单号：" + result.order().getOrderNo()
                + "，手续费：" + result.refundRecord().getRefundFee()
                + "元，预计退款：" + result.refundRecord().getRefundAmount()
                + "元，退款将在5-10个工作日原路退回。");
    }

    @Tool("查询当前系统中的铁路购票订单")
    public ToolResult queryOrders() {
        List<TicketOrder> orders = ticketOrderService.listActiveOrders();
        if (orders.isEmpty()) {
            return new ToolResult(true, "当前没有已订票订单。已退票订单不会在对话里展示。");
        }

        StringBuilder builder = new StringBuilder("查询到以下已订票订单：");
        for (int i = 0; i < orders.size(); i++) {
            TicketOrder order = orders.get(i);
            builder.append("\n")
                    .append(i + 1)
                    .append(". ")
                    .append(formatOrderBrief(order));
        }
        builder.append("\n\n如需退票，请发送订单号，或发送“我要退票 + 姓名 + 身份证号”。");
        return new ToolResult(true, builder.toString());
    }

    @Tool("查询指定城市的实时天气，供铁路出行前参考")
    public ToolResult queryWeather(@P("城市名称") String city) {
        String cleanCity = city == null || city.isBlank() ? "广州" : city.trim();
        try {
            return new ToolResult(true, qWeatherClient.queryNow(cleanCity).toTravelMessage());
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden exception) {
            log.warn("QWeather auth failed for city {}: {}", cleanCity, exception.getMessage());
            return new ToolResult(false, "和风天气凭据或 API 权限不足，请在和风控制台检查 API KEY、API Host、应用限制和 API 限制。");
        } catch (RuntimeException exception) {
            log.warn("QWeather query failed for city {}: {}", cleanCity, exception.getMessage());
            return new ToolResult(false, cleanCity + "真实天气查询暂时失败，请稍后重试。");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatOrderBrief(TicketOrder order) {
        return "订单号：" + order.getOrderNo()
                + "，乘车人：" + order.getPassengerName()
                + "，车次：" + order.getTrainNo()
                + "，日期：" + order.getTravelDate()
                + "，座位：" + order.getSeatType() + " " + order.getSeatNo()
                + "，票价：" + order.getPrice() + "元"
                + "，状态：" + order.getStatus();
    }
}
