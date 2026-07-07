package org.gecedu.ticketassistant.order;

public record RefundRequest(
        String orderNo,
        String passengerName,
        String idCard
) {
}
