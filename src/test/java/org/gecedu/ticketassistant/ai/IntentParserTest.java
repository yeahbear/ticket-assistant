package org.gecedu.ticketassistant.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IntentParserTest {

    @Test
    void parseBookingInfoFromNaturalLanguage() {
        BookingInfo info = IntentParser.parseBooking("我要购票，乘车人姓名张三，身份证号440111199901011234，车次G101，乘车日期2026-06-25，座位类型硬座");

        assertThat(info.complete()).isTrue();
        assertThat(info.passengerName()).isEqualTo("张三");
        assertThat(info.idCard()).isEqualTo("440111199901011234");
        assertThat(info.trainNo()).isEqualTo("G101");
        assertThat(info.travelDate()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(info.seatType()).isEqualTo("硬座");
    }

    @Test
    void parseRouteAndRealSeatType() {
        BookingInfo info = IntentParser.parseBooking("我要购票，从广州到上海，乘车人姓名张三，身份证号440111199901011234，车次G246，乘车日期2026-07-15，座位类型二等座");

        assertThat(info.complete()).isTrue();
        assertThat(info.depart()).isEqualTo("广州");
        assertThat(info.arrive()).isEqualTo("上海");
        assertThat(info.seatType()).isEqualTo("二等座");
        assertThat(IntentParser.isTrainTicketSearchIntent("查询广州到上海 2026-07-15 车票")).isTrue();
    }

    @Test
    void detectTrainTicketSearchWithoutRoute() {
        assertThat(IntentParser.isTrainTicketSearchIntent("查询余票")).isTrue();
        assertThat(IntentParser.parseRoute("查询余票")).isNull();
    }

    @Test
    void reportMissingBookingFields() {
        BookingInfo info = IntentParser.parseBooking("我要买票，姓名李四");

        assertThat(info.complete()).isFalse();
        assertThat(info.missing()).contains("身份证号", "车次", "乘车日期，格式如2026-06-25", "座位类型：商务座/一等座/二等座/硬座/软座/硬卧/软卧/无座");
    }

    @Test
    void parseRefundByOrderNo() {
        RefundInfo info = IntentParser.parseRefund("我要退票，订单号TA1783000000000123");

        assertThat(info.complete()).isTrue();
        assertThat(info.orderNo()).isEqualTo("TA1783000000000123");
    }

    @Test
    void parseWeatherCity() {
        assertThat(IntentParser.parseWeatherCity("查询广州天气")).isEqualTo("广州");
    }

    @Test
    void detectOrderQueryIntent() {
        assertThat(IntentParser.isOrderQueryIntent("查询我的订单")).isTrue();
        assertThat(IntentParser.isOrderQueryIntent("我有哪些订单")).isTrue();
    }
}
