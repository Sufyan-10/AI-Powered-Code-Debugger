package com.aidebugger.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiDebuggerApplication {

    public static void main(String[] args) {
    	
    	// Load .env variables
        Dotenv dotenv = Dotenv.load();
        System.setProperty("GEMINI_API_KEY", dotenv.get("GEMINI_API_KEY"));
        System.setProperty("GEMINI_MODEL", dotenv.get("GEMINI_MODEL"));
        
        SpringApplication.run(AiDebuggerApplication.class, args);
        System.out.println("✅ AI Debugger Backend is running...");
    }
}
