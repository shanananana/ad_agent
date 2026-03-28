package com.shanananana.adagent.creative;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 素材页「技能一键」HTTP 请求内：将通用出图工具的返回 JSON 挂到 {@link HttpServletRequest}，供同线程内组装 API 响应。
 */
public final class MaterialCreativeToolCapture {

    public static final String ATTR_CAPTURE = "materialCreativeToolCapture";
    public static final String ATTR_LAST_GENERATE_JSON = "lastGenerateImageToolJson";

    private MaterialCreativeToolCapture() {
    }

    public static void tryStoreGenerateJson(String json) {
        if (json == null) {
            return;
        }
        HttpServletRequest req = currentRequest();
        if (req != null && Boolean.TRUE.equals(req.getAttribute(ATTR_CAPTURE))) {
            req.setAttribute(ATTR_LAST_GENERATE_JSON, json);
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
