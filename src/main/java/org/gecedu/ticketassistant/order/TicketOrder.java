package org.gecedu.ticketassistant.order;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ticket_order")
public class TicketOrder {

    @TableId
    private Long id;
    private String orderNo;
    private String passengerName;
    private String idCard;
    private String trainNo;
    private LocalDate travelDate;
    private String seatType;
    private String seatNo;
    private BigDecimal price;
    private String status;
    private LocalDateTime bookTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
