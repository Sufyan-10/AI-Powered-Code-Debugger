package com.aidebugger.backend.model;

public class CodeResponse {
    private String output;
    private String error;
    private String compileOutput;
    private String correctedCode;
    private String explanation;

    // getters & setters
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getCompileOutput() { return compileOutput; }
    public void setCompileOutput(String compileOutput) { this.compileOutput = compileOutput; }

    public String getCorrectedCode() { return correctedCode; }
    public void setCorrectedCode(String correctedCode) { this.correctedCode = correctedCode; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
