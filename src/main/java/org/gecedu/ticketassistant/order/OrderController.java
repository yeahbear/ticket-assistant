package org.gecedu.ticketassistant.order;

import org.gecedu.ticketassistant.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final TicketOrderService ticketOrderService;

    public OrderController(TicketOrderService ticketOrderService) {
        this.ticketOrderService = ticketOrderService;
    }

    @GetMapping("/list")
    public ApiResponse<List<TicketOrder>> listOrders() {
        return ApiResponse.ok(ticketOrderService.listOrders());
    }

    @GetMapping("/refund/list")
    public ApiResponse<List<RefundRecord>> listRefunds() {
        return ApiResponse.ok(ticketOrderService.listRefunds());
    }

    @DeleteMapping("/demo-data")
    public ApiResponse<Void> clearDemoData() {
        ticketOrderService.clearDemoData();
        return ApiResponse.ok(null);
    }
}
