package org.gecedu.ticketassistant.session;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId
    private Long id;
    private String title;
    private String userCode;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
