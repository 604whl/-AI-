package com.shortvideoscripagent.xhsagentyunying.ai.agent;

/**
 * Callbacks emitted while the agent loop runs (tool progress + assistant text chunks).
 */
public interface AgentStreamListener {

    void onStepStart(int step, int maxSteps);

    void onToolStart(String tool, int step);

    void onToolEnd(String tool, boolean success, long latencyMs, String error);

    void onDelta(String contentChunk);

    void onDone(AgentResponse response);

    void onError(int code, String message);

    /** No-op listener for synchronous {@link AgentOrchestrator#run} calls. */
    AgentStreamListener NOOP = new AgentStreamListener() {
        @Override
        public void onStepStart(int step, int maxSteps) {
        }

        @Override
        public void onToolStart(String tool, int step) {
        }

        @Override
        public void onToolEnd(String tool, boolean success, long latencyMs, String error) {
        }

        @Override
        public void onDelta(String contentChunk) {
        }

        @Override
        public void onDone(AgentResponse response) {
        }

        @Override
        public void onError(int code, String message) {
        }
    };
}
