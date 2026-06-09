package com.shanananana.adagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ad-agent.rag")
public class RagProperties {

    private boolean enabled = false;
    private int topK = 5;
    private double similarityThreshold = 0.55;
    private boolean diagnostics = false;
    private boolean reindexOnStartup = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public boolean isDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(boolean diagnostics) {
        this.diagnostics = diagnostics;
    }

    public boolean isReindexOnStartup() {
        return reindexOnStartup;
    }

    public void setReindexOnStartup(boolean reindexOnStartup) {
        this.reindexOnStartup = reindexOnStartup;
    }
}
