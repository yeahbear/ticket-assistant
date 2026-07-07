package org.gecedu.ticketassistant.ai;

import java.time.LocalDate;
import java.util.List;

public record BookingInfo(
        String passengerName,
        String idCard,
        String trainNo,
        LocalDate travelDate,
        String seatType,
        List<String> missing
) {
    public boolean complete() {
        return missing.isEmpty();
    }
}
