package org.gecedu.ticketassistant.chat;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
