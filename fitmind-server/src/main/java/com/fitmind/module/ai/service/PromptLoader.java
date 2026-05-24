package com.fitmind.module.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import jakarta.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 提示词加载器：在 Spring 启动时扫描 classpath:prompts/*.xml，
 * 解析 &lt;system&gt; 和 &lt;user&gt; 块，按 prompt id 缓存。
 */
@Slf4j
@Component
public class PromptLoader {

    /** key: promptId, value: [system文本, user模板文本] */
    private final Map<String, String[]> cache = new HashMap<>();

    @PostConstruct
    public void load() {
        doLoad();
    }

    /**
     * 热重载提示词（修改 XML 后无需重启服务）
     */
    public int reload() {
        cache.clear();
        doLoad();
        return cache.size();
    }

    private void doLoad() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:prompts/*.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Document doc = builder.parse(is);
                    doc.getDocumentElement().normalize();

                    String promptId = doc.getDocumentElement().getAttribute("id");
                    if (promptId == null || promptId.isBlank()) {
                        log.warn("[PromptLoader] 文件 {} 缺少 id 属性，跳过", resource.getFilename());
                        continue;
                    }

                    String system = extractCdata(doc, "system");
                    String user = extractCdata(doc, "user");

                    cache.put(promptId, new String[]{system, user});
                    log.info("[PromptLoader] 已加载提示词: id={}, file={}", promptId, resource.getFilename());
                } catch (Exception e) {
                    log.error("[PromptLoader] 解析文件 {} 失败: {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("[PromptLoader] 共加载 {} 个提示词", cache.size());
        } catch (Exception e) {
            log.error("[PromptLoader] 扫描提示词目录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取 system 块内容
     */
    public String getSystem(String promptId) {
        String[] entry = cache.get(promptId);
        if (entry == null) {
            log.warn("[PromptLoader] 未找到 promptId={} 的提示词", promptId);
            return "";
        }
        return entry[0];
    }

    /**
     * 获取 user 块模板内容（含 {{占位符}}）
     */
    public String getUserTemplate(String promptId) {
        String[] entry = cache.get(promptId);
        if (entry == null) {
            log.warn("[PromptLoader] 未找到 promptId={} 的提示词", promptId);
            return "";
        }
        return entry[1];
    }

    private String extractCdata(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().trim();
    }
}
