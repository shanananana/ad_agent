package com.example.adagent.agent;

/**
 * 编排器返回的<strong>单轮对话结果</strong>：面向用户的 {@code response} 文本，以及是否在隐私清除等场景下
 * 建议浏览器整页刷新（以同步会话列表与本地 {@code sessionId} 等状态）。
 */
public record ChatTurnResult(String response, boolean suggestPageRefresh) {

    public static ChatTurnResult of(String response) {
        return new ChatTurnResult(response, false);
    }

    public static ChatTurnResult of(String response, boolean suggestPageRefresh) {
        return new ChatTurnResult(response, suggestPageRefresh);
    }
}
