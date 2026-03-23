package com.example.adagent.agent.memory;

import com.example.adagent.data.LongTermMemoryRepository;
import com.example.adagent.data.dto.LongTermMemoryFile;
import com.example.adagent.prompt.ClasspathPromptLoader;
import com.example.adagent.prompt.PromptResourcePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <strong>长期记忆</strong>：按 {@code userId} 读写 {@code data/long_term_memory/{userId}.json}，
 * 检索时取最近若干条注入上下文；每轮结束后由 LLM 判断是否应将本轮信息摘要为新的长期记忆并做去重合并。
 */
@Service
public class LongTermMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryService.class);
    private static final Pattern SHOULD_SAVE_PATTERN = Pattern.compile("(需要保存|应该保存|存入|保存|是\\s*[：:]\\s*true|shouldSave\\s*[:=]\\s*true)", Pattern.CASE_INSENSITIVE);

    private final LongTermMemoryRepository repository;
    private final ChatClient chatClient;
    private final ClasspathPromptLoader classpathPromptLoader;

    public LongTermMemoryService(
            LongTermMemoryRepository repository,
            ChatClient chatClient,
            ClasspathPromptLoader classpathPromptLoader) {
        this.repository = repository;
        this.chatClient = chatClient;
        this.classpathPromptLoader = classpathPromptLoader;
    }

    /**
     * 检索用户最近长期记忆，用于注入上下文（按时间取最近 N 条）。
     */
    public List<MemorySummary> retrieveRelevantMemories(String userId, String query, int topK) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<LongTermMemoryFile.MemoryEntry> entries = repository.getRecent(userId, topK);
        return entries.stream()
                .map(e -> new MemorySummary(null, e.getSummary(), 0))
                .collect(Collectors.toList());
    }

    private static final int RECENT_MEMORIES_FOR_DEDUP = 5;

    /**
     * 根据近期对话内容，经 LLM 汇总并判断是否与「用户习惯、投放偏好」相关且与已有记忆不重复；若相关且不重复则写入。
     */
    public void saveSummaryFromChat(String userId, String sessionId, String conversationSummary) {
        if (userId == null || userId.isBlank() || conversationSummary == null || conversationSummary.isBlank()) {
            return;
        }
        try {
            List<LongTermMemoryFile.MemoryEntry> recent = repository.getRecent(userId, RECENT_MEMORIES_FOR_DEDUP);
            String recentBlock = recent.isEmpty()
                    ? "（暂无）"
                    : recent.stream()
                            .map(e -> "- " + (e.getSummary() != null ? e.getSummary() : ""))
                            .filter(s -> !"- ".equals(s))
                            .collect(Collectors.joining("\n"));
            String prompt = classpathPromptLoader.renderTemplate(
                    PromptResourcePaths.LONG_TERM_MEMORY_JUDGE,
                    Map.of("recentMemoriesBlock", recentBlock, "conversationSummary", conversationSummary));
            String judgment = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (judgment == null) {
                return;
            }
            boolean shouldSave = SHOULD_SAVE_PATTERN.matcher(judgment).find()
                    || judgment.trim().toLowerCase().startsWith("需要保存")
                    || judgment.trim().toLowerCase().startsWith("应该保存");
            String summaryToSave = extractSummaryFromJudgment(judgment);
            if (shouldSave && summaryToSave != null && !summaryToSave.isBlank()) {
                if (isDuplicateOfRecent(summaryToSave, recent)) {
                    logger.debug("【长期记忆】与已有记忆相似，跳过写入 userId={}", userId);
                    return;
                }
                repository.append(userId, summaryToSave);
                logger.info("【长期记忆】已写入 userId={}, 摘要长度={}", userId, summaryToSave.length());
            } else {
                logger.debug("【长期记忆】无需写入 userId={}, 判断: {}", userId, judgment.substring(0, Math.min(100, judgment.length())));
            }
        } catch (Exception e) {
            logger.warn("【长期记忆】LLM 判断/汇总失败，跳过写入: {}", e.getMessage());
        }
    }

    /** 简单启发式：新摘要与最近几条在关键信息上高度重叠则视为重复，避免重复写入 */
    private boolean isDuplicateOfRecent(String newSummary, List<LongTermMemoryFile.MemoryEntry> recent) {
        if (newSummary == null || newSummary.isBlank() || recent == null || recent.isEmpty()) {
            return false;
        }
        String norm = normalizeForCompare(newSummary);
        if (norm.length() < 4) return false;
        for (LongTermMemoryFile.MemoryEntry e : recent) {
            String existing = e.getSummary();
            if (existing == null || existing.isBlank()) continue;
            String existingNorm = normalizeForCompare(existing);
            if (existingNorm.contains(norm) || norm.contains(existingNorm)) return true;
            if (jaccardSimilarity(norm, existingNorm) >= 0.65) return true;
        }
        return false;
    }

    private static String normalizeForCompare(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\p{P}]+", "").toLowerCase();
    }

    private static double jaccardSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        java.util.Set<String> setA = new java.util.HashSet<>();
        for (int i = 0; i <= a.length() - 2; i++) {
            setA.add(a.substring(i, i + 2));
        }
        java.util.Set<String> setB = new java.util.HashSet<>();
        for (int i = 0; i <= b.length() - 2; i++) {
            setB.add(b.substring(i, i + 2));
        }
        int inter = 0;
        for (String x : setA) {
            if (setB.contains(x)) inter++;
        }
        int union = setA.size() + setB.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    private static String extractSummaryFromJudgment(String judgment) {
        if (judgment == null) return null;
        String s = judgment.trim();
        int start = s.indexOf("摘要：");
        if (start >= 0) {
            s = s.substring(start + "摘要：".length()).trim();
        } else {
            int idx = s.indexOf("\n");
            if (idx > 0 && (s.toLowerCase().startsWith("需要保存") || s.toLowerCase().startsWith("应该保存"))) {
                s = s.substring(idx).trim();
            }
        }
        if (s.length() > 500) {
            s = s.substring(0, 500) + "…";
        }
        return s.isBlank() ? null : s;
    }

    public static class MemorySummary {
        private final String sessionId;
        private final String summary;
        private final long timestamp;

        public MemorySummary(String sessionId, String summary, long timestamp) {
            this.sessionId = sessionId;
            this.summary = summary;
            this.timestamp = timestamp;
        }

        public String getSessionId() { return sessionId; }
        public String getSummary() { return summary; }
        public long getTimestamp() { return timestamp; }
    }
}
