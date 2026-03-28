package com.shanananana.adagent.skills;

import org.springframework.ai.chat.client.ChatClient;

public interface MarkdownSkillsChatClientSupport {

    void applyTo(ChatClient.Builder builder);
}
