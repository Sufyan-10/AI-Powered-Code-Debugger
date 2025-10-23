package com.aidebugger.backend.model;

public class CodeRequest {
    private String code;
    private String language;
    private String input;
    private String description;

    // getters & setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
