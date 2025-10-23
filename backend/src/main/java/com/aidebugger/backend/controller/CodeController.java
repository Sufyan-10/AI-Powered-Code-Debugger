package com.aidebugger.backend.controller;

import com.aidebugger.backend.model.CodeRequest;
import com.aidebugger.backend.model.CodeResponse;
import com.aidebugger.backend.service.LocalExecutorService;
import com.aidebugger.backend.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CodeController {

    private static final Logger logger = LoggerFactory.getLogger(CodeController.class);

    @Autowired
    private LocalExecutorService localExecutorService;

    @Autowired(required = false)
    private GeminiService geminiService;

    @PostMapping("/run")
    public ResponseEntity<CodeResponse> runCode(@RequestBody CodeRequest request) {
        try {
            if (request == null || request.getCode() == null || request.getCode().trim().isEmpty()) {
                CodeResponse bad = new CodeResponse();
                bad.setError("Code is empty. Please provide source code.");
                return ResponseEntity.badRequest().body(bad);
            }
            if (request.getCode().length() > 300_000) {
                CodeResponse r = new CodeResponse();
                r.setError("Source too large. Maximum allowed size is 300 KB.");
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(r);
            }
            if (request.getInput() != null && request.getInput().length() > 50_000) {
                CodeResponse r = new CodeResponse();
                r.setError("Input too large. Maximum allowed stdin size is 50 KB.");
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(r);
            }

            CodeResponse resp = localExecutorService.executeCode(request);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            logger.error("Unexpected error in /api/run", ex);
            CodeResponse r = new CodeResponse();
            r.setError("Server error while running code: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
        }
    }

    @PostMapping("/ai-debug")
    public ResponseEntity<CodeResponse> aiDebug(@RequestBody CodeRequest request) {
        try {
            if (request == null || request.getCode() == null || request.getCode().trim().isEmpty()) {
                CodeResponse bad = new CodeResponse();
                bad.setError("Code is empty. Please provide source code for AI debugging.");
                return ResponseEntity.badRequest().body(bad);
            }
            if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
                CodeResponse bad = new CodeResponse();
                bad.setError("Description/prompt is required for AI Debug. Please explain what the code should do.");
                return ResponseEntity.badRequest().body(bad);
            }

            if (geminiService == null) {
                CodeResponse r = new CodeResponse();
                r.setError("Server-side GeminiService is not configured. Please implement GeminiService and restart.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(r);
            }

            CodeResponse runResult = localExecutorService.executeCode(request);

            if (runResult.getCompileOutput() != null && !runResult.getCompileOutput().trim().isEmpty()) {
                CodeResponse compileErrResp = new CodeResponse();
                compileErrResp.setCompileOutput(runResult.getCompileOutput());
                compileErrResp.setError("Compilation failed. Fix syntax errors first.");
                return ResponseEntity.ok(compileErrResp);
            }

            StringBuilder augmentedDescription = new StringBuilder();
            augmentedDescription.append(request.getDescription().trim());
            augmentedDescription.append("\n\n--- Run context (automatically collected) ---\n");
            if ((runResult.getOutput() != null && !runResult.getOutput().isBlank())) {
                augmentedDescription.append("Program stdout:\n").append(runResult.getOutput()).append("\n");
            }
            if (runResult.getError() != null && !runResult.getError().isBlank()) {
                augmentedDescription.append("Program runtime stderr/error:\n").append(runResult.getError()).append("\n");
            }

            CodeRequest forAi = new CodeRequest();
            forAi.setCode(request.getCode());
            forAi.setLanguage(request.getLanguage());
            forAi.setDescription(augmentedDescription.toString());

            CodeResponse aiResp = geminiService.debugCode(forAi);

            if ((aiResp == null) || ((aiResp.getCorrectedCode() == null || aiResp.getCorrectedCode().isBlank())
                    && (aiResp.getExplanation() == null || aiResp.getExplanation().isBlank()))) {
                CodeResponse fallback = new CodeResponse();
                fallback.setExplanation("AI did not return a usable response. Here is the program's runtime info.");
                fallback.setOutput(runResult.getOutput());
                fallback.setError(runResult.getError());
                return ResponseEntity.ok(fallback);
            }

            if (aiResp.getExplanation() == null) aiResp.setExplanation("");
            aiResp.setExplanation(aiResp.getExplanation() + "\n\n--- Previous run output (for reference) ---\n" +
                    (runResult.getOutput() == null ? "" : runResult.getOutput()) +
                    (runResult.getError() == null ? "" : ("\nErrors:\n" + runResult.getError()))
            );

            return ResponseEntity.ok(aiResp);

        } catch (Exception ex) {
            logger.error("Unexpected error in /api/ai-debug", ex);
            CodeResponse r = new CodeResponse();
            r.setError("Server error while performing AI debug: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
        }
    }
}
