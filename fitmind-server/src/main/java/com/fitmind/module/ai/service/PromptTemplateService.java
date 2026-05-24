package com.fitmind.module.ai.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 提示词模板渲染服务：将模板中的 {{KEY}} 替换为实际值。
 */
@Service
public class PromptTemplateService {

    /**
     * 将 template 中所有 {{key}} 替换为 variables 中对应值。
     * 若某个 key 在 variables 中不存在，则替换为空字符串。
     *
     * @param template  包含 {{KEY}} 占位符的模板字符串
     * @param variables 占位符 -> 实际值 映射
     * @return 渲染后的字符串
     */
    public String render(String template, Map<String, String> variables) {
        if (template == null || template.isBlank()) return "";
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
