package org.gecedu.ticketassistant.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 100, message = "会话标题不能超过100个字符")
        String title
) {
}
