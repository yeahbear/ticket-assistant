package org.gecedu.ticketassistant.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketOrderServiceTest {

    @Test
    void listActiveOrdersOnlyReturnsBookedOrders() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        service.listActiveOrders();

        verify(ticketOrderMapper).selectList(any());
    }

    @Test
    void clearDemoDataDeletesRefundRecordsAndOrders() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        service.clearDemoData();

        verify(refundRecordMapper).delete(any());
        verify(ticketOrderMapper).delete(any());
    }

    @Test
    void refundByPassengerInfoRequiresSelectionWhenMultipleOrdersExist() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(
                order("TA1783000000000111", "G101"),
                order("TA1783000000000222", "G102")
        ));
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        assertThatThrownBy(() -> service.refund(new RefundRequest(null, "张三", "440111199901011234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("检测到有多个可退票订单")
                .hasMessageContaining("TA1783000000000111")
                .hasMessageContaining("TA1783000000000222");
        verify(ticketOrderMapper, never()).updateById(any(TicketOrder.class));
        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
    }

    @Test
    void bookingRejectsExpiredTravelDate() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        assertThatThrownBy(() -> service.book(new BookingRequest(
                "张三",
                "440111199901011234",
                "G101",
                LocalDate.now().minusDays(1),
                "硬座"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("订票截止时间");
        verify(ticketOrderMapper, never()).insert(any(TicketOrder.class));
    }

    @Test
    void bookingRejectsDuplicateActiveOrderForSamePassengerTrainDateAndSeatType() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        when(ticketOrderMapper.selectCount(any())).thenReturn(1L);
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        assertThatThrownBy(() -> service.book(new BookingRequest(
                "张三",
                "440111199901011234",
                "G101",
                LocalDate.now().plusDays(10),
                "硬座"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在相同乘车人");
        verify(ticketOrderMapper, never()).insert(any(TicketOrder.class));
    }

    @Test
    void bookingRejectsInvalidSeatType() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        assertThatThrownBy(() -> service.book(new BookingRequest(
                "张三",
                "440111199901011234",
                "G101",
                LocalDate.now().plusDays(10),
                "商务座"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("座位类型必须是");
        verify(ticketOrderMapper, never()).insert(any(TicketOrder.class));
    }

    @Test
    void bookingRejectsMissingRequiredField() {
        TicketOrderMapper ticketOrderMapper = mock(TicketOrderMapper.class);
        RefundRecordMapper refundRecordMapper = mock(RefundRecordMapper.class);
        TicketOrderService service = new TicketOrderService(ticketOrderMapper, refundRecordMapper);

        assertThatThrownBy(() -> service.book(new BookingRequest(
                " ",
                "440111199901011234",
                "G101",
                LocalDate.now().plusDays(10),
                "硬座"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("乘车人姓名不能为空");
        verify(ticketOrderMapper, never()).insert(any(TicketOrder.class));
    }

    private TicketOrder order(String orderNo, String trainNo) {
        TicketOrder order = new TicketOrder();
        order.setOrderNo(orderNo);
        order.setPassengerName("张三");
        order.setIdCard("440111199901011234");
        order.setTrainNo(trainNo);
        order.setTravelDate(LocalDate.now().plusDays(10));
        order.setSeatType("硬座");
        order.setSeatNo("3车12A");
        order.setPrice(new BigDecimal("128.00"));
        order.setStatus("已订票");
        return order;
    }
}
