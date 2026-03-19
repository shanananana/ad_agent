package com.example.adagent.agent;

/**
 * 单轮对话结果：回复文本 + 是否建议客户端刷新页面（如隐私数据清除成功后会话列表与本地状态需重置）。
 */
public record ChatTurnResult(String response, boolean suggestPageRefresh) {

    public static ChatTurnResult of(String response) {
        return new ChatTurnResult(response, false);
    }

    public static ChatTurnResult of(String response, boolean suggestPageRefresh) {
        return new ChatTurnResult(response, suggestPageRefresh);
    }
}
