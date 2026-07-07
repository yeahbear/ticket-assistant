package org.gecedu.ticketassistant.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionService {

    private final ChatSessionMapper sessionMapper;

    public SessionService(ChatSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    public List<ChatSession> list() {
        List<ChatSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getDeleted, 0)
                .orderByDesc(ChatSession::getUpdateTime));
        sessions.forEach(session -> session.setTitle(displayTitle(session)));
        return sessions;
    }

    @Transactional
    public ChatSession create(String title) {
        ChatSession session = new ChatSession();
        session.setTitle(cleanInputTitle(title, "新会话"));
        session.setUserCode("demo-user");
        session.setDeleted(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Transactional
    public ChatSession update(Long id, String title) {
        ChatSession session = requireSession(id);
        session.setTitle(cleanInputTitle(title, "会话 " + id));
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return session;
    }

    @Transactional
    public void delete(Long id) {
        ChatSession session = requireSession(id);
        session.setDeleted(1);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public ChatSession requireSession(Long id) {
        ChatSession session = sessionMapper.selectById(id);
        if (session == null || Integer.valueOf(1).equals(session.getDeleted())) {
            throw new IllegalArgumentException("会话不存在");
        }
        return session;
    }

    private String displayTitle(ChatSession session) {
        String title = session.getTitle();
        if (looksCorrupted(title)) {
            return "会话 " + session.getId();
        }
        return title;
    }

    private String cleanInputTitle(String title, String fallback) {
        if (title == null || title.isBlank() || looksCorrupted(title)) {
            return fallback;
        }
        return title.trim();
    }

    private boolean looksCorrupted(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        long questionMarks = text.chars().filter(ch -> ch == '?').count();
        return questionMarks >= 2 || text.contains("�");
    }
}
