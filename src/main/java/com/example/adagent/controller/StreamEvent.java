package com.example.adagent.controller;

/**
 * 流式响应事件：思考过程 或 内容片段
 */
public record StreamEvent(String type, String data) {
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_DONE = "done";

    public static StreamEvent thinking(String data) {
        return new StreamEvent(TYPE_THINKING, data);
    }

    public static StreamEvent content(String data) {
        return new StreamEvent(TYPE_CONTENT, data);
    }

    public static StreamEvent done(String data) {
        return new StreamEvent(TYPE_DONE, data);
    }
}
