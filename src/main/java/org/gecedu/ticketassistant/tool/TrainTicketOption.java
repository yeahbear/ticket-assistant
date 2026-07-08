package org.gecedu.ticketassistant.tool;

import java.time.LocalDate;
import java.util.List;

public record TrainTicketOption(
        String trainNo,
        String trainOrder,
        String departIndex,
        String arriveIndex,
        String departName,
        String arriveName,
        String departTime,
        String arriveTime,
        String duration,
        String seatCode,
        LocalDate date,
        List<TrainSeatStock> seats
) {

    public TrainSeatStock seat(String seatType) {
        String target = ApiHzTicketClient.apiSeatName(seatType);
        return seats.stream()
                .filter(item -> item.type().startsWith(target) || target.startsWith(item.type()))
                .findFirst()
                .orElse(null);
    }
}
