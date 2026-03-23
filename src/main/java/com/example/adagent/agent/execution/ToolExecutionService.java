package com.example.adagent.agent.execution;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class ToolExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionService.class);
    private static final int MAX_BUFFER_BEFORE_FLUSH = 16_000;

    private final ChatClient chatClient;

    public ToolExecutionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String executeWithTools(String userInput) {
        logger.info("【工具执行层】执行工具调用，输入长度: {}", userInput != null ? userInput.length() : 0);
        String response = chatClient.prompt()
                .user(userInput)
                .call()
                .content();
        logger.info("【工具执行层】工具调用完成");
        return response;
    }

    /**
     * 原始流式输出（无过滤）。
     */
    public Flux<String> executeWithToolsStream(String userInput) {
        logger.info("【工具执行层】流式执行工具调用");
        return chatClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }

    /**
     * 对话 UI 用流式输出：去掉「为了…将调用…。」等开场白，正文从首个 {@code ### } 标题起展示；超长无标题时整段放行。
     *
     * @param strippedNarrationSink 非空时，在检测到完整开场白被剥离时回调一次（可写入「思考过程」）
     */
    public Flux<String> executeWithToolsStreamForUi(String userInput, Consumer<String> strippedNarrationSink) {
        logger.info("【工具执行层】流式执行工具调用（UI 过滤）");
        Flux<String> source = chatClient.prompt()
                .user(userInput)
                .stream()
                .content();
        return applyUiStreamFilter(source, strippedNarrationSink);
    }

    static Flux<String> applyUiStreamFilter(Flux<String> source, Consumer<String> strippedNarrationSink) {
        GateState st = new GateState();
        Flux<String> main = source.handle((chunk, sink) -> {
            if (chunk == null) {
                return;
            }
            for (String out : st.addChunk(chunk, strippedNarrationSink)) {
                sink.next(out);
            }
        });
        Flux<String> tail = Mono.fromCallable(() -> st.finish(strippedNarrationSink))
                .flatMapMany(Flux::fromIterable);
        return Flux.concat(main, tail);
    }

    static final class GateState {
        private final StringBuilder buf = new StringBuilder();
        private boolean started;
        private boolean narrationReported;

        List<String> addChunk(String chunk, Consumer<String> narrationSink) {
            List<String> outs = new ArrayList<>();
            buf.append(chunk);
            if (started) {
                outs.add(chunk);
                return outs;
            }
            String full = buf.toString();
            ReplySanitizer.PrefixStrip ps = ReplySanitizer.stripToolNarrationPrefixIfComplete(full);
            reportNarrationIfNeeded(ps.removed(), narrationSink);
            String work = ps.remainder();
            int h = ReplySanitizer.indexOfFirstMarkdownHeading(work);
            if (h >= 0) {
                started = true;
                buf.setLength(0);
                outs.add(work.substring(h));
                return outs;
            }
            if (full.length() >= MAX_BUFFER_BEFORE_FLUSH) {
                started = true;
                buf.setLength(0);
                outs.add(work);
                return outs;
            }
            return outs;
        }

        List<String> finish(Consumer<String> narrationSink) {
            if (started) {
                return List.of();
            }
            String full = buf.toString();
            ReplySanitizer.PrefixStrip ps = ReplySanitizer.stripToolNarrationPrefixIfComplete(full);
            reportNarrationIfNeeded(ps.removed(), narrationSink);
            String work = ps.remainder();
            int h = ReplySanitizer.indexOfFirstMarkdownHeading(work);
            started = true;
            buf.setLength(0);
            if (h >= 0) {
                return List.of(work.substring(h));
            }
            return List.of(work);
        }

        private void reportNarrationIfNeeded(String removed, Consumer<String> narrationSink) {
            if (removed == null || removed.isBlank() || narrationReported || narrationSink == null) {
                return;
            }
            narrationReported = true;
            narrationSink.accept(removed.trim());
        }
    }
}
