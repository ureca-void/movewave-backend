package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenAiTestController {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @GetMapping("/api/openai/test")
    public String testOpenAiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return "OpenAI API key is missing";
        }

        return "OpenAI API key loaded";
    }
}