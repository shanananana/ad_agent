package com.example.adagent.creative;

import com.example.adagent.data.ContentCatalogRepository;
import com.example.adagent.data.GlobalCreativeRepository;
import com.example.adagent.data.dto.ContentCatalogFile;
import com.example.adagent.data.dto.ContentItem;
import com.example.adagent.data.dto.GlobalCreativeFile;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * <strong>内容库与全局素材目录</strong> REST：读写用户维度 {@code contents.json}/{@code creatives.json}，
 * 以及将素材绑定到广告组等操作，供 {@code chat.html} 素材 Tab 与看板使用。
 */
@RestController
@RequestMapping("/api/ad-agent/catalog")
public class CatalogController {

    private final ContentCatalogRepository contentCatalogRepository;
    private final GlobalCreativeRepository globalCreativeRepository;
    private final CreativeCatalogService creativeCatalogService;

    public CatalogController(ContentCatalogRepository contentCatalogRepository,
                             GlobalCreativeRepository globalCreativeRepository,
                             CreativeCatalogService creativeCatalogService) {
        this.contentCatalogRepository = contentCatalogRepository;
        this.globalCreativeRepository = globalCreativeRepository;
        this.creativeCatalogService = creativeCatalogService;
    }

    private static String uid(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId 不能为空");
        }
        return userId.trim();
    }

    @GetMapping("/contents")
    public ContentCatalogFile listContents(@RequestParam("userId") String userId) {
        return contentCatalogRepository.load(uid(userId));
    }

    @PostMapping("/contents")
    public Map<String, Object> createContent(@RequestBody Map<String, String> body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String userId = uid(body.get("userId"));
        String name = body.getOrDefault("name", "");
        String summary = body.getOrDefault("summary", "");
        ContentItem item = creativeCatalogService.createContent(userId, name, summary);
        Map<String, Object> out = new HashMap<>();
        out.put("content", item);
        return out;
    }

    @GetMapping("/creatives")
    public GlobalCreativeFile listCreatives(@RequestParam("userId") String userId) {
        return globalCreativeRepository.load(uid(userId));
    }

    @PostMapping("/bind-creative")
    public Map<String, String> bindCreative(@RequestBody Map<String, Object> body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String userId = uid(String.valueOf(body.get("userId")));
        String campaignId = require(body.get("campaignId"), "campaignId");
        String adGroupId = require(body.get("adGroupId"), "adGroupId");
        String creativeId = require(body.get("creativeId"), "creativeId");
        boolean prepend = Boolean.TRUE.equals(body.get("prepend"));
        try {
            creativeCatalogService.bindCreativeToAdGroup(userId, campaignId, adGroupId, creativeId, prepend);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        Map<String, String> ok = new HashMap<>();
        ok.put("status", "ok");
        return ok;
    }

    private static String require(Object v, String name) {
        if (v == null || !StringUtils.hasText(String.valueOf(v))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 " + name);
        }
        return String.valueOf(v).trim();
    }
}
