package com.shanananana.adagent.controller;

/**
 * 对话 <strong>SSE（Server-Sent Events）</strong> 中的单条事件：{@code type} 区分思考过程、正文增量、结束标记，
 * 以及需前端执行的 {@link #TYPE_CLIENT} 指令（如清除成功后刷新页面）；{@code data} 为对应负载字符串。
 */
public record StreamEvent(String type, String data) {
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_DONE = "done";
    /** 前端指令，如清除成功后建议整页刷新；data 为 JSON，例如 {"reload":true} */
    public static final String TYPE_CLIENT = "client";

    public static StreamEvent thinking(String data) {
        return new StreamEvent(TYPE_THINKING, data);
    }

    public static StreamEvent content(String data) {
        return new StreamEvent(TYPE_CONTENT, data);
    }

    public static StreamEvent done(String data) {
        return new StreamEvent(TYPE_DONE, data);
    }

    public static StreamEvent client(String jsonPayload) {
        return new StreamEvent(TYPE_CLIENT, jsonPayload);
    }
}
