package com.lbq.ai_chat_project.ollama.dto.model;

import lombok.Data;

import java.util.List;

@Data
public class OllamaModelsResponse {
    private List<Model> models;

    @Data
    public static class Model {
        private String name;
        private String modifiedAt;
        private long size;
    }
}