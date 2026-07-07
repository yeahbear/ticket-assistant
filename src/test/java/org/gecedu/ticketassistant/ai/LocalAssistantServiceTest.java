package org.gecedu.ticketassistant.ai;

import org.gecedu.ticketassistant.chat.ChatMessage;
import org.gecedu.ticketassistant.tool.TicketToolService;
import org.gecedu.ticketassistant.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalAssistantServiceTest {

    @Test
    void bookingPromptIsReadableList() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("我要购票", List.of());

        assertThat(answer)
                .contains("可以为您办理购票。")
                .contains("订票需要提供以下信息：")
                .contains("1. 乘车人姓名")
                .contains("当前还缺：")
                .contains("\n\n");
    }

    @Test
    void refundPromptIsReadableList() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("我要退票", List.of());

        assertThat(answer)
                .contains("可以为您办理退票。")
                .contains("退票有两种方式：")
                .contains("1. 提供订单号")
                .contains("当前还缺：")
                .contains("\n\n");
    }

    @Test
    void orderQueryUsesTicketTool() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        when(ticketToolService.queryOrders()).thenReturn(new ToolResult(true, "查询到以下订单：\n1. 订单号：TA1783000000000123"));
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("查询我的订单", List.of());

        assertThat(answer).contains("查询到以下订单", "TA1783000000000123");
        verify(ticketToolService).queryOrders();
    }

    @Test
    void refundSelectionUsesOrderNoFromPreviousCandidateList() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        when(ticketToolService.refundTicket("TA1783000000000222", null, null))
                .thenReturn(new ToolResult(true, "退票成功，订单号：TA1783000000000222"));
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("退第2个", List.of(
                message("assistant", """
                        退票暂未完成：检测到有多个可退票订单，请选择要退票的订单号：
                        1. 订单号：TA1783000000000111，车次：G101
                        2. 订单号：TA1783000000000222，车次：G102
                        """)
        ));

        assertThat(answer).contains("退票成功", "TA1783000000000222");
        verify(ticketToolService).refundTicket("TA1783000000000222", null, null);
    }

    @Test
    void currentRefundIntentWinsOverPreviousBookingHistory() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        when(ticketToolService.refundTicket(anyString(), isNull(), isNull()))
                .thenReturn(new ToolResult(true, "退票成功，订单号：TA1783320038896383"));
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("我要退票，订单号TA1783320038896383", List.of(
                message("user", "我要购票，乘车人姓名张三，身份证号440111199901011234，车次G101，乘车日期2026-08-25，座位类型硬座"),
                message("assistant", "购票成功，订单号：TA1783320038896383")
        ));

        assertThat(answer).contains("退票成功");
        verify(ticketToolService).refundTicket("TA1783320038896383", null, null);
        verify(ticketToolService, never()).bookTicket(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void bookingContinuationStillBooksTicket() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        when(ticketToolService.bookTicket(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ToolResult(true, "购票成功，订单号：TA1783321000000001"));
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("乘车人姓名李四，身份证号440111199901011234，车次G102，乘车日期2026-08-25，座位类型硬卧", List.of(
                message("user", "我要购票"),
                message("assistant", "可以为您办理购票。根据知识库规则，订票需要提供：乘车人姓名、身份证号、车次、乘车日期，格式如2026-06-25、座位类型：硬座/软座/硬卧/软卧。请一次性补充这些信息，我会为您生成购票订单。")
        ));

        assertThat(answer).contains("购票成功");
        verify(ticketToolService).bookTicket("李四", "440111199901011234", "G102", "2026-08-25", "硬卧");
        verify(ticketToolService, never()).refundTicket(anyString(), anyString(), anyString());
    }

    @Test
    void bookingServiceErrorReturnsReadableMessage() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        when(ticketToolService.bookTicket(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("已存在相同乘车人、车次、日期和座位类型的有效订单，请勿重复购票"));
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("我要购票，乘车人姓名张三，身份证号440111199901011234，车次G101，乘车日期2026-08-25，座位类型硬座", List.of());

        assertThat(answer)
                .contains("购票暂未完成")
                .contains("请勿重复购票")
                .contains("重新提交");
    }

    @Test
    void newBookingIntentDoesNotReusePreviousCompletedBookingInfo() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("我要购票", List.of(
                message("user", "我要购票，乘车人姓名张三，身份证号440111199901011234，车次G103，乘车日期2026-08-25，座位类型硬座"),
                message("assistant", "购票成功，订单号：TA1783391102865847，车次：G103，日期：2026-08-25，座位：硬座9车8F，票价：128.00元。")
        ));

        assertThat(answer).contains("订票需要提供以下信息", "当前还缺");
        verify(ticketToolService, never()).bookTicket(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void bookingPromptExamplesDoNotSatisfyMissingFields() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("乘车人姓名张三", List.of(
                message("user", "我要购票"),
                message("assistant", """
                        可以为您办理购票。

                        订票需要提供以下信息：
                        1. 乘车人姓名
                        2. 身份证号
                        3. 车次
                        4. 乘车日期，格式如 2026-06-25
                        5. 座位类型：硬座 / 软座 / 硬卧 / 软卧

                        当前还缺：乘车人姓名、身份证号、车次、乘车日期，格式如2026-06-25、座位类型：硬座/软座/硬卧/软卧
                        请一次性补充这些信息，我会为您生成购票订单。
                        """)
        ));

        assertThat(answer).contains("身份证号", "车次", "乘车日期", "座位类型");
        verify(ticketToolService, never()).bookTicket(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void newRefundIntentDoesNotReusePreviousOrderNo() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("我要退票", List.of(
                message("assistant", "退票成功，订单号：TA1783000000000123，手续费：12.80元，预计退款：115.20元。")
        ));

        assertThat(answer).contains("退票有两种方式", "当前还缺");
        verify(ticketToolService, never()).refundTicket(anyString(), anyString(), anyString());
    }

    @Test
    void weatherIntentWithoutCityAsksForCity() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("查询天气", List.of());

        assertThat(answer).contains("请告诉我要查询哪个城市");
        verify(ticketToolService, never()).queryWeather(anyString());
    }

    @Test
    void identityQuestionDuringPendingBookingDoesNotContinueBooking() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("你知道我叫什么吗", List.of(
                message("user", "我叫王五"),
                message("assistant", """
                        可以为您办理购票。

                        订票需要提供以下信息：
                        1. 乘车人姓名
                        2. 身份证号
                        3. 车次
                        4. 乘车日期，格式如 2026-06-25
                        5. 座位类型：硬座 / 软座 / 硬卧 / 软卧
                        """)
        ));

        assertThat(answer).contains("王五");
        verify(ticketToolService, never()).bookTicket(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unrelatedTextDuringPendingBookingDoesNotBookTicket() {
        TicketToolService ticketToolService = mock(TicketToolService.class);
        LocalAssistantService service = new LocalAssistantService(new KnowledgeBaseService(), ticketToolService);

        String answer = service.answer("先等一下", List.of(
                message("assistant", "可以为您办理购票。\n\n订票需要提供以下信息：")
        ));

        assertThat(answer).contains("智能票务助手");
        verify(ticketToolService, never()).bookTicket(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }
}
