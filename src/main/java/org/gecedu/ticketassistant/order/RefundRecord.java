package org.gecedu.ticketassistant.order;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_record")
public class RefundRecord {

    @TableId
    private Long id;
    private String orderNo;
    private String passengerName;
    private String idCard;
    private BigDecimal feeRate;
    private BigDecimal refundFee;
    private BigDecimal refundAmount;
    private String reason;
    private LocalDateTime createTime;
}
