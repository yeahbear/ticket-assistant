package org.gecedu.ticketassistant.ai;

import java.util.List;

public record RefundInfo(
        String orderNo,
        String passengerName,
        String idCard,
        List<String> missing
) {
    public boolean complete() {
        return orderNo != null || (passengerName != null && idCard != null);
    }
}
