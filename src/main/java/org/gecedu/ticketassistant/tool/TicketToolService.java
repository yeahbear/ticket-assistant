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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TicketToolService {

    private static final Logger log = LoggerFactory.getLogger(TicketToolService.class);

    private final TicketOrderService ticketOrderService;
    private final QWeatherClient qWeatherClient;
    private final ApiHzTicketClient apiHzTicketClient;

    public TicketToolService(TicketOrderService ticketOrderService, QWeatherClient qWeatherClient, ApiHzTicketClient apiHzTicketClient) {
        this.ticketOrderService = ticketOrderService;
        this.qWeatherClient = qWeatherClient;
        this.apiHzTicketClient = apiHzTicketClient;
    }

    @Tool("根据乘车人信息完成铁路购票，并在数据库中创建购票订单")
    public ToolResult bookTicket(
            @P("乘车人姓名") String passengerName,
            @P("身份证号") String idCard,
            @P("车次") String trainNo,
            @P("乘车日期，格式为yyyy-MM-dd") String travelDate,
            @P("座位类型：商务座/一等座/二等座/硬座/软座/硬卧/软卧/无座") String seatType
    ) {
        return bookTicket(passengerName, idCard, trainNo, travelDate, seatType, null, null);
    }

    public ToolResult bookTicket(
            String passengerName,
            String idCard,
            String trainNo,
            String travelDate,
            String seatType,
            String depart,
            String arrive
    ) {
        LocalDate parsedTravelDate;
        try {
            parsedTravelDate = LocalDate.parse(travelDate == null ? "" : travelDate.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("乘车日期格式应为 yyyy-MM-dd，例如 2026-08-25");
        }
        TrainTicketOption realTicket = findRealTicket(depart, arrive, parsedTravelDate, trainNo, seatType);
        BigDecimal realPrice = null;
        if (realTicket != null) {
            realPrice = apiHzTicketClient.queryPrice(realTicket, seatType);
        }
        TicketOrder order = ticketOrderService.book(new BookingRequest(
                trimToEmpty(passengerName),
                trimToEmpty(idCard),
                trimToEmpty(trainNo),
                parsedTravelDate,
                trimToEmpty(seatType)
        ), realPrice);
        return new ToolResult(true, "购票成功，订单号：" + order.getOrderNo()
                + "，车次：" + order.getTrainNo()
                + "，日期：" + order.getTravelDate()
                + "，座位：" + order.getSeatType() + order.getSeatNo()
                + "，票价：" + order.getPrice() + "元。"
                + (realTicket == null ? "" : "\n已按真实余票接口校验：" + realTicket.departName() + " " + realTicket.departTime()
                + " → " + realTicket.arriveName() + " " + realTicket.arriveTime() + "。"));
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

    @Tool("查询指定出发地、目的地和日期的真实火车票余票")
    public ToolResult queryTrainTickets(
            @P("出发地或出发站") String depart,
            @P("目的地或目的站") String arrive,
            @P("乘车日期，格式为yyyy-MM-dd") String travelDate
    ) {
        LocalDate parsedTravelDate;
        try {
            parsedTravelDate = LocalDate.parse(travelDate == null ? "" : travelDate.trim());
        } catch (RuntimeException exception) {
            return new ToolResult(false, "乘车日期格式应为 yyyy-MM-dd，例如 2026-08-25。");
        }
        try {
            List<TrainTicketOption> options = apiHzTicketClient.search(trimToEmpty(depart), trimToEmpty(arrive), parsedTravelDate);
            if (options.isEmpty()) {
                return new ToolResult(true, "没有查询到 " + depart + " 到 " + arrive + " 在 " + parsedTravelDate + " 的可展示车次。");
            }
            return new ToolResult(true, formatTrainTickets(depart, arrive, parsedTravelDate, options));
        } catch (RuntimeException exception) {
            log.warn("ApiHz train ticket query failed: {}", exception.getMessage());
            return new ToolResult(false, "真实车票接口暂时查询失败，已保留本地模拟购票能力。原因：" + exception.getMessage());
        }
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

    private TrainTicketOption findRealTicket(String depart, String arrive, LocalDate travelDate, String trainNo, String seatType) {
        if (depart == null || depart.isBlank() || arrive == null || arrive.isBlank() || !apiHzTicketClient.enabled()) {
            return null;
        }
        try {
            TrainTicketOption option = apiHzTicketClient.findTrain(depart.trim(), arrive.trim(), travelDate, trimToEmpty(trainNo));
            if (option == null) {
                throw new IllegalArgumentException("真实余票校验未找到 " + depart + " 到 " + arrive + " 的 " + trainNo + " 次列车");
            }
            TrainSeatStock stock = option.seat(seatType);
            if (stock == null) {
                throw new IllegalArgumentException(trainNo + " 次列车没有 " + seatType + " 席别");
            }
            if (!stock.available()) {
                throw new IllegalArgumentException(trainNo + " 次列车 " + seatType + " 已无票");
            }
            return option;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Real ticket validation failed, fallback to local booking: {}", exception.getMessage());
            return null;
        }
    }

    private String formatTrainTickets(String depart, String arrive, LocalDate travelDate, List<TrainTicketOption> options) {
        StringBuilder builder = new StringBuilder("查询到 ")
                .append(depart)
                .append(" 到 ")
                .append(arrive)
                .append(" 在 ")
                .append(travelDate)
                .append(" 的真实余票：");
        int limit = Math.min(options.size(), 8);
        for (int i = 0; i < limit; i++) {
            TrainTicketOption option = options.get(i);
            builder.append("\n")
                    .append(i + 1)
                    .append(". ")
                    .append(option.trainNo())
                    .append("  ")
                    .append(option.departName())
                    .append(" ")
                    .append(option.departTime())
                    .append(" → ")
                    .append(option.arriveName())
                    .append(" ")
                    .append(option.arriveTime())
                    .append("，耗时 ")
                    .append(option.duration())
                    .append("，")
                    .append(formatAvailableSeats(option));
        }
        builder.append("\n\n购票时请补充：乘车人姓名、身份证号、车次、日期、座位类型。");
        return builder.toString();
    }

    private String formatAvailableSeats(TrainTicketOption option) {
        List<String> seats = option.seats().stream()
                .filter(TrainSeatStock::available)
                .map(seat -> seat.type() + seat.stockText())
                .limit(4)
                .toList();
        return seats.isEmpty() ? "暂无可售席别" : String.join("，", seats);
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
