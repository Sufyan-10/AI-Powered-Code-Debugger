package com.aidebugger.backend.service;

import com.aidebugger.backend.model.CodeRequest;
import com.aidebugger.backend.model.CodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String geminiApiKeyProperty;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModelProperty;

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

    private final WebClient webClient;

    public GeminiService() {
        this.webClient = WebClient.builder().build();
    }

    public CodeResponse debugCode(CodeRequest request) {
        CodeResponse resp = new CodeResponse();

        String apiKey = resolveApiKey();
        String model = resolveModel();

        System.out.println("[GeminiService] resolved apiKey present? " + (apiKey != null && !apiKey.isBlank()) + ", model=" + model);

        if (apiKey == null || apiKey.isBlank()) {
            resp.setError("Gemini API key not configured. Set GEMINI_API_KEY as an env var, system property, or in application.properties (gemini.api.key).");
            return resp;
        }

        try {
            String prompt = buildPrompt(request);
            String url = String.format("%s/%s:generateContent", GEMINI_BASE, model);

            Map<String, Object> payload = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            );


            System.out.println("[GeminiService] Calling Gemini at: " + url);
            // Log trimmed prompt size for debugging
            System.out.println("[GeminiService] Prompt (first 800 chars): " + (prompt.length() > 800 ? prompt.substring(0, 800) + "..." : prompt));

            Map<?, ?> apiResp = webClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResp -> clientResp.bodyToMono(String.class).flatMap(body ->
                                    reactor.core.publisher.Mono.error(new RuntimeException("HTTP " + clientResp.statusCode() + ": " + body))
                            ))
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (apiResp == null) {
                resp.setError("Empty response from Gemini.");
                return resp;
            }

            // Debug: print top-level keys
            System.out.println("[GeminiService] Response keys: " + apiResp.keySet());

            // Parse generated text defensively
            String fullText = extractTextFromResponse(apiResp);

            // Debug output length
            System.out.println("[GeminiService] fullText length: " + (fullText == null ? 0 : fullText.length()));

            if (fullText == null) fullText = "";

            String explanation = fullText.trim();
            String correctedCode = "";

            int startFence = fullText.indexOf("```");
            if (startFence >= 0) {
                int endFence = fullText.indexOf("```", startFence + 3);
                if (endFence > startFence) {
                    int firstLineBreak = fullText.indexOf('\n', startFence + 3);
                    if (firstLineBreak > startFence + 3 && firstLineBreak < endFence) {
                        correctedCode = fullText.substring(firstLineBreak + 1, endFence).trim();
                    } else {
                        correctedCode = fullText.substring(startFence + 3, endFence).trim();
                    }
                    explanation = (startFence > 0) ? fullText.substring(0, startFence).trim() : "";
                }
            }

            resp.setExplanation(explanation);
            resp.setCorrectedCode(correctedCode);
            return resp;

        } catch (WebClientResponseException wex) {
            String body = wex.getResponseBodyAsString();
            resp.setError("Gemini HTTP error: " + wex.getRawStatusCode() + " - " + (body == null ? wex.getMessage() : body));
            System.err.println("[GeminiService] WebClientResponseException: " + resp.getError());
            return resp;
        } catch (Exception ex) {
            resp.setError("Gemini API error: " + ex.getMessage());
            System.err.println("[GeminiService] Exception: " + ex.toString());
            return resp;
        }
    }

    private String extractTextFromResponse(Map<?, ?> apiResp) {
        // Common shapes:
        // { "candidates": [ { "content": { "parts": [ {"text": "..." } ] } } ] }
        // or { "candidates": [ { "text": "..." } ] }
        try {
            Object candidatesObj = apiResp.get("candidates");
            if (candidatesObj instanceof List && !((List<?>) candidatesObj).isEmpty()) {
                Object first = ((List<?>) candidatesObj).get(0);
                if (first instanceof Map) {
                    Map<?, ?> firstMap = (Map<?, ?>) first;
                    Object content = firstMap.get("content");
                    if (content instanceof Map) {
                        Object parts = ((Map<?, ?>) content).get("parts");
                        if (parts instanceof List && !((List<?>) parts).isEmpty()) {
                            Object p0 = ((List<?>) parts).get(0);
                            if (p0 instanceof Map) {
                                Object text = ((Map<?, ?>) p0).get("text");
                                if (text instanceof String) return (String) text;
                            } else if (p0 instanceof String) {
                                return p0.toString();
                            }
                        }
                    }
                    // fallback
                    Object text = firstMap.get("text");
                    if (text instanceof String) return (String) text;
                }
            }
            // fallback to top-level "content" if present
            Object contentTop = apiResp.get("content");
            if (contentTop instanceof Map) {
                Object parts = ((Map<?, ?>) contentTop).get("parts");
                if (parts instanceof List && !((List<?>) parts).isEmpty()) {
                    Object p0 = ((List<?>) parts).get(0);
                    if (p0 instanceof Map) {
                        Object text = ((Map<?, ?>) p0).get("text");
                        if (text instanceof String) return (String) text;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GeminiService] extractTextFromResponse error: " + e.toString());
        }
        return "";
    }

    private String resolveApiKey() {
        if (geminiApiKeyProperty != null && !geminiApiKeyProperty.isBlank()) return geminiApiKeyProperty;
        String s = System.getProperty("GEMINI_API_KEY");
        if (s != null && !s.isBlank()) return s;
        s = System.getenv("GEMINI_API_KEY");
        if (s != null && !s.isBlank()) return s;
        s = System.getProperty("gemini.api.key");
        if (s != null && !s.isBlank()) return s;
        return null;
    }

    private String resolveModel() {
        if (geminiModelProperty != null && !geminiModelProperty.isBlank()) return geminiModelProperty;
        String s = System.getProperty("GEMINI_MODEL");
        if (s != null && !s.isBlank()) return s;
        s = System.getenv("GEMINI_MODEL");
        if (s != null && !s.isBlank()) return s;
        return "gemini-2.5-flash";
    }

    private String buildPrompt(CodeRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert programmer and tutor. ");
        sb.append("Given a program and a user's description of what the program should do, ");
        sb.append("find logical errors (not only syntax) and suggest a corrected version of the code. ");
        sb.append("If the code has syntax errors, point them out. ");
        sb.append("If the code is already correct, reply: 'Your code is ready to run' and give a short explanation of what it does. ");
        sb.append("Always provide corrected code inside a fenced code block with triple backticks and the language if possible.\n\n");

        sb.append("User description:\n").append(req.getDescription() == null ? "" : req.getDescription()).append("\n\n");
        sb.append("Original code:\n```\n").append(req.getCode() == null ? "" : req.getCode()).append("\n```\n\n");
        sb.append("Return an explanation and the corrected code (if needed). Keep explanation concise and actionable.");
        return sb.toString();
    }
}
