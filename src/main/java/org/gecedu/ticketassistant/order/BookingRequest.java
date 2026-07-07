package org.gecedu.ticketassistant.order;

import java.time.LocalDate;

public record BookingRequest(
        String passengerName,
        String idCard,
        String trainNo,
        LocalDate travelDate,
        String seatType
) {
}
