package com.fitmind.module.ai.controller;

import com.fitmind.common.api.Result;
import com.fitmind.module.ai.service.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPromptController {

    private final PromptLoader promptLoader;

    @PostMapping("/prompts/reload")
    public Result<String> reloadPrompts() {
        int count = promptLoader.reload();
        return Result.success("提示词已重载，共 " + count + " 个");
    }
}
