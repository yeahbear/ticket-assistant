package org.gecedu.ticketassistant.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TicketOrderService {

    private final TicketOrderMapper ticketOrderMapper;
    private final RefundRecordMapper refundRecordMapper;

    public TicketOrderService(TicketOrderMapper ticketOrderMapper, RefundRecordMapper refundRecordMapper) {
        this.ticketOrderMapper = ticketOrderMapper;
        this.refundRecordMapper = refundRecordMapper;
    }

    public List<TicketOrder> listOrders() {
        return ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .orderByDesc(TicketOrder::getCreateTime));
    }

    public List<TicketOrder> listActiveOrders() {
        return ticketOrderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, "已订票")
                .orderByDesc(TicketOrder::getCreateTime));
    }

    public List<RefundRecord> listRefunds() {
        return refundRecordMapper.selectList(new LambdaQueryWrapper<RefundRecord>()
                .orderByDesc(RefundRecord::getCreateTime));
    }

    @Transactional
    public void clearDemoData() {
        refundRecordMapper.delete(new LambdaQueryWrapper<RefundRecord>()
                .isNotNull(RefundRecord::getId));
        ticketOrderMapper.delete(new LambdaQueryWrapper<TicketOrder>()
                .isNotNull(TicketOrder::getId));
    }

    @Transactional
    public TicketOrder book(BookingRequest request) {
        return book(request, null);
    }

    @Transactional
    public TicketOrder book(BookingRequest request, BigDecimal realPrice) {
        BookingRequest cleanRequest = validateAndNormalizeBookingRequest(request);
        validateBookingDeadline(cleanRequest.travelDate());
        String trainNo = cleanRequest.trainNo();
        validateNoDuplicateActiveOrder(cleanRequest, trainNo);
        TicketOrder order = new TicketOrder();
        order.setOrderNo(createOrderNo());
        order.setPassengerName(cleanRequest.passengerName());
        order.setIdCard(cleanRequest.idCard());
        order.setTrainNo(trainNo);
        order.setTravelDate(cleanRequest.travelDate());
        order.setSeatType(cleanRequest.seatType());
        order.setSeatNo(createSeatNo(trainNo, cleanRequest.travelDate(), cleanRequest.seatType()));
        order.setPrice(realPrice == null ? calculatePrice(cleanRequest.seatType()) : realPrice);
        order.setStatus("已订票");
        order.setBookTime(LocalDateTime.now());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        ticketOrderMapper.insert(order);
        return order;
    }

    @Transactional
    public RefundResult refund(RefundRequest request) {
        TicketOrder order = findRefundTarget(request);
        if (!"已订票".equals(order.getStatus())) {
            throw new IllegalArgumentException("订单当前状态为" + order.getStatus() + "，不能重复退票");
        }

        BigDecimal feeRate = refundFeeRate(order.getTravelDate());
        BigDecimal refundFee = order.getPrice().multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = order.getPrice().subtract(refundFee).setScale(2, RoundingMode.HALF_UP);

        order.setStatus("已退票");
        order.setRefundTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        ticketOrderMapper.updateById(order);

        RefundRecord record = new RefundRecord();
        record.setOrderNo(order.getOrderNo());
        record.setPassengerName(order.getPassengerName());
        record.setIdCard(order.getIdCard());
        record.setFeeRate(feeRate);
        record.setRefundFee(refundFee);
        record.setRefundAmount(refundAmount);
        record.setReason("按铁路票务规则自动计算退票手续费");
        record.setCreateTime(LocalDateTime.now());
        refundRecordMapper.insert(record);

        return new RefundResult(order, record);
    }

    private TicketOrder findRefundTarget(RefundRequest request) {
        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();
        if (request.orderNo() != null && !request.orderNo().isBlank()) {
            wrapper.eq(TicketOrder::getOrderNo, request.orderNo());
            TicketOrder order = ticketOrderMapper.selectOne(wrapper);
            if (order == null) {
                throw new IllegalArgumentException("未找到可退票订单");
            }
            return order;
        }

        if (request.passengerName() == null || request.idCard() == null) {
            throw new IllegalArgumentException("退票需要提供订单号，或乘车人姓名+身份证号");
        }

        List<TicketOrder> orders = ticketOrderMapper.selectList(wrapper
                .eq(TicketOrder::getPassengerName, request.passengerName())
                .eq(TicketOrder::getIdCard, request.idCard())
                .eq(TicketOrder::getStatus, "已订票")
                .orderByDesc(TicketOrder::getCreateTime));
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("未找到可退票订单");
        }
        if (orders.size() > 1) {
            throw new IllegalArgumentException(formatMultipleRefundCandidates(orders));
        }
        return orders.get(0);
    }

    private String formatMultipleRefundCandidates(List<TicketOrder> orders) {
        StringBuilder builder = new StringBuilder("检测到有多个可退票订单，请选择要退票的订单号：");
        for (int i = 0; i < orders.size(); i++) {
            TicketOrder order = orders.get(i);
            builder.append("\n")
                    .append(i + 1)
                    .append(". 订单号：")
                    .append(order.getOrderNo())
                    .append("，车次：")
                    .append(order.getTrainNo())
                    .append("，日期：")
                    .append(order.getTravelDate())
                    .append("，座位：")
                    .append(order.getSeatType())
                    .append(" ")
                    .append(order.getSeatNo())
                    .append("，票价：")
                    .append(order.getPrice())
                    .append("元");
        }
        return builder.toString();
    }

    private BookingRequest validateAndNormalizeBookingRequest(BookingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("购票信息不能为空");
        }
        String passengerName = cleanRequired(request.passengerName(), "乘车人姓名不能为空");
        String idCard = cleanRequired(request.idCard(), "身份证号不能为空");
        String trainNo = cleanRequired(request.trainNo(), "车次不能为空").toUpperCase(Locale.ROOT);
        if (request.travelDate() == null) {
            throw new IllegalArgumentException("乘车日期不能为空");
        }
        String seatType = cleanRequired(request.seatType(), "座位类型不能为空");
        validateSeatType(seatType);
        return new BookingRequest(passengerName, idCard, trainNo, request.travelDate(), seatType);
    }

    private String cleanRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private void validateSeatType(String seatType) {
        switch (seatType) {
            case "商务座", "一等座", "二等座", "硬座", "软座", "硬卧", "软卧", "无座" -> {
            }
            default -> throw new IllegalArgumentException("座位类型必须是：商务座/一等座/二等座/硬座/软座/硬卧/软卧/无座");
        }
    }

    private BigDecimal refundFeeRate(LocalDate travelDate) {
        LocalDateTime trainTime = travelDate.atTime(8, 0);
        if (LocalDateTime.now().isAfter(trainTime)) {
            throw new IllegalArgumentException("列车已开车，不能退票");
        }
        long hours = Duration.between(LocalDateTime.now(), trainTime).toHours();
        return hours >= 24 ? new BigDecimal("0.10") : new BigDecimal("0.20");
    }

    private void validateBookingDeadline(LocalDate travelDate) {
        LocalDateTime trainTime = travelDate.atTime(8, 0);
        if (!LocalDateTime.now().isBefore(trainTime.minusMinutes(30))) {
            throw new IllegalArgumentException("订票截止时间：开车前30分钟停止订票");
        }
    }

    private void validateNoDuplicateActiveOrder(BookingRequest request, String trainNo) {
        Long count = ticketOrderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getPassengerName, request.passengerName())
                .eq(TicketOrder::getIdCard, request.idCard())
                .eq(TicketOrder::getTrainNo, trainNo)
                .eq(TicketOrder::getTravelDate, request.travelDate())
                .eq(TicketOrder::getSeatType, request.seatType())
                .eq(TicketOrder::getStatus, "已订票"));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("已存在相同乘车人、车次、日期和座位类型的有效订单，请勿重复购票");
        }
    }

    private BigDecimal calculatePrice(String seatType) {
        return switch (seatType) {
            case "商务座" -> new BigDecimal("960.00");
            case "一等座" -> new BigDecimal("680.00");
            case "二等座" -> new BigDecimal("420.00");
            case "软卧" -> new BigDecimal("560.00");
            case "硬卧" -> new BigDecimal("320.00");
            case "软座" -> new BigDecimal("210.00");
            case "硬座" -> new BigDecimal("128.00");
            case "无座" -> new BigDecimal("128.00");
            default -> throw new IllegalArgumentException("座位类型必须是：商务座/一等座/二等座/硬座/软座/硬卧/软卧/无座");
        };
    }

    private String createOrderNo() {
        return "TA" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    private String createSeatNo(String trainNo, LocalDate travelDate, String seatType) {
        for (int i = 0; i < 100; i++) {
            String seatNo = randomSeatNo();
            Long count = ticketOrderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                    .eq(TicketOrder::getTrainNo, trainNo)
                    .eq(TicketOrder::getTravelDate, travelDate)
                    .eq(TicketOrder::getSeatType, seatType)
                    .eq(TicketOrder::getSeatNo, seatNo)
                    .eq(TicketOrder::getStatus, "已订票"));
            if (count == null || count == 0) {
                return seatNo;
            }
        }
        throw new IllegalStateException("当前车次可用座位不足，请稍后重试");
    }

    private String randomSeatNo() {
        int carriage = ThreadLocalRandom.current().nextInt(1, 13);
        int row = ThreadLocalRandom.current().nextInt(1, 20);
        char seat = (char) ('A' + ThreadLocalRandom.current().nextInt(0, 6));
        return carriage + "车" + row + seat;
    }
}
