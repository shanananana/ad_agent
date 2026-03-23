package com.example.adagent.creative.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuggestPromptResponse {

    private String status;
    /** 可直接填入文生图的描述正文 */
    private String prompt;
    private String message;
    /** 参与排序的素材 ID 列表（便于调试） */
    private List<String> referenceCreativeIds = new ArrayList<>();

    public static SuggestPromptResponse ok(String prompt, List<String> referenceCreativeIds) {
        SuggestPromptResponse r = new SuggestPromptResponse();
        r.status = "ok";
        r.prompt = prompt;
        if (referenceCreativeIds != null) {
            r.referenceCreativeIds = referenceCreativeIds;
        }
        return r;
    }

    public static SuggestPromptResponse error(String message) {
        SuggestPromptResponse r = new SuggestPromptResponse();
        r.status = "error";
        r.message = message;
        return r;
    }

    public String getStatus() {
        return status;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getReferenceCreativeIds() {
        return referenceCreativeIds;
    }
}
