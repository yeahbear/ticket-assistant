package org.gecedu.ticketassistant.order;

public record RefundResult(TicketOrder order, RefundRecord refundRecord) {
}
