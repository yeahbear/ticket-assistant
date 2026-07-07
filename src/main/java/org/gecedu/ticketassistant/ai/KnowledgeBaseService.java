package org.gecedu.ticketassistant.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class KnowledgeBaseService {

    private final String content;

    public KnowledgeBaseService() {
        this.content = loadKnowledge();
    }

    public String content() {
        return content;
    }

    public String retrieve(String question) {
        if (question.contains("退票") || question.contains("退款") || question.contains("退订")) {
            return section("2. 我要退票");
        }
        if (question.contains("购票") || question.contains("买票") || question.contains("订票")) {
            return section("1. 我要购票");
        }
        return content;
    }

    private String section(String marker) {
        int start = content.indexOf(marker);
        if (start < 0) {
            return content;
        }
        int nextMarker = marker.startsWith("1.") ? content.indexOf("2. 我要退票", start + marker.length()) : -1;
        int next = nextMarker > 0 ? nextMarker : content.length();
        return next < 0 ? content.substring(start) : content.substring(start, next);
    }

    private String loadKnowledge() {
        try {
            ClassPathResource resource = new ClassPathResource("rag/rag-service.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("无法加载RAG知识库文件", ex);
        }
    }
}
