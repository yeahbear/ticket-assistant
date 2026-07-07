package org.gecedu.ticketassistant.tool;

import org.gecedu.ticketassistant.order.BookingRequest;
import org.gecedu.ticketassistant.order.RefundRecord;
import org.gecedu.ticketassistant.order.RefundRequest;
import org.gecedu.ticketassistant.order.RefundResult;
import org.gecedu.ticketassistant.order.TicketOrder;
import org.gecedu.ticketassistant.order.TicketOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketToolServiceTest {

    @Mock
    private TicketOrderService ticketOrderService;

    @Mock
    private QWeatherClient qWeatherClient;

    @InjectMocks
    private TicketToolService ticketToolService;

    @Test
    void bookTicketCallsOrderService() {
        TicketOrder order = new TicketOrder();
        order.setOrderNo("TA1783000000000123");
        order.setTrainNo("G101");
        order.setTravelDate(LocalDate.of(2026, 6, 25));
        order.setSeatType("硬座");
        order.setSeatNo("3车12A");
        order.setPrice(new BigDecimal("128.00"));
        when(ticketOrderService.book(org.mockito.ArgumentMatchers.any(BookingRequest.class))).thenReturn(order);

        ToolResult result = ticketToolService.bookTicket("张三", "440111199901011234", "G101", "2026-06-25", "硬座");

        ArgumentCaptor<BookingRequest> captor = ArgumentCaptor.forClass(BookingRequest.class);
        verify(ticketOrderService).book(captor.capture());
        assertThat(captor.getValue().passengerName()).isEqualTo("张三");
        assertThat(result.message()).contains("购票成功", "TA1783000000000123");
    }

    @Test
    void bookTicketRejectsInvalidDateWithReadableMessage() {
        assertThatThrownBy(() -> ticketToolService.bookTicket(
                "张三",
                "440111199901011234",
                "G101",
                "2026/06/25",
                "硬座"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("乘车日期格式应为 yyyy-MM-dd");
    }

    @Test
    void refundTicketCallsOrderService() {
        TicketOrder order = new TicketOrder();
        order.setOrderNo("TA1783000000000123");
        RefundRecord record = new RefundRecord();
        record.setRefundFee(new BigDecimal("12.80"));
        record.setRefundAmount(new BigDecimal("115.20"));
        when(ticketOrderService.refund(org.mockito.ArgumentMatchers.any(RefundRequest.class))).thenReturn(new RefundResult(order, record));

        ToolResult result = ticketToolService.refundTicket("TA1783000000000123", "", "");

        verify(ticketOrderService).refund(org.mockito.ArgumentMatchers.any(RefundRequest.class));
        assertThat(result.message()).contains("退票成功", "手续费：12.80", "5-10个工作日");
    }

    @Test
    void queryWeatherUsesQWeather() {
        when(qWeatherClient.queryNow("广州"))
                .thenReturn(new WeatherNow("广州", "多云", "26", "北风", "2", "70"));

        ToolResult result = ticketToolService.queryWeather("广州");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("广州实时天气", "多云", "26");
    }

    @Test
    void queryOrdersReturnsReadableOrderList() {
        TicketOrder order = new TicketOrder();
        order.setOrderNo("TA1783000000000123");
        order.setPassengerName("张三");
        order.setTrainNo("G101");
        order.setTravelDate(LocalDate.of(2026, 8, 25));
        order.setSeatType("硬座");
        order.setSeatNo("3车12A");
        order.setPrice(new BigDecimal("128.00"));
        order.setStatus("已订票");
        when(ticketOrderService.listActiveOrders()).thenReturn(java.util.List.of(order));

        ToolResult result = ticketToolService.queryOrders();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("查询到以下已订票订单", "TA1783000000000123", "张三", "G101", "已订票");
        verify(ticketOrderService).listActiveOrders();
    }

    @Test
    void queryOrdersDoesNotShowRefundedOrders() {
        when(ticketOrderService.listActiveOrders()).thenReturn(java.util.List.of());

        ToolResult result = ticketToolService.queryOrders();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("当前没有已订票订单");
    }
}
