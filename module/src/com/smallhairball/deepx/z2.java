package com.smallhairball.deepx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/**
 * Local API contract shared between the main DEX and the encrypted payload.  The payload's
 * gateway implements the HTTP server and calls back through {@link Backend}; the main DEX
 * (Main) implements {@link Backend} and drives the native DeepSeek transport.  Keeping this
 * contract in the main DEX (and hiding the full gateway implementation in the payload) keeps
 * the DEX-to-payload reference surface as small as possible.
 */
public final class z2 {
    /** Must match the independently compiled, server-keyed Local API payload. */
    public static final int LOCAL_API_PAYLOAD_ABI = 4;
    static final String PROTOCOL_OPENAI = "openai";
    static final String PROTOCOL_ANTHROPIC = "anthropic";
    static final long COMPLETION_REQUEST_BUDGET_MS = 600_000L;

    private z2() {}

    public interface Backend {
        public boolean isReady();
        public String readinessDetail();
        public CompletionResult complete(CompletionRequest request, DeltaSink sink) throws Exception;
    }

    public interface DeltaSink {
        /** Called once the native completion Flow has actually been entered. */
        public default void onUpstreamStarted() throws Exception {}
        /** Return false when the client connection is gone and upstream collection should stop. */
        public boolean onText(String delta) throws Exception;
        public boolean onReasoning(String delta) throws Exception;
        public boolean isCancelled();
        /** True once a complete structured action has been captured; not a client cancellation. */
        public default boolean isSatisfied() { return false; }
        /** Text already committed to the external client, used for in-request stream recovery. */
        public default String publishedTextSnapshot() { return ""; }
    }

    /** Minimal tool-plan contract: the payload's Plan exposes only what the request needs. */
    public interface ToolPlan {
        public boolean active();
    }

    public static final class CompletionRequest {
        public final String requestId;
        public final String requestedModel;
        public final String nativeModel;
        /** Conversation without the request-scoped tool protocol, used for continuation state. */
        public final String basePrompt;
        public final String prompt;
        public final boolean reasoning;
        public final boolean search;
        public final int maxOutputTokens;
        /** Native DeepSeek file ids created by the oversized-prompt relay. */
        public final List<String> nativeFileIds;
        /** Validated inline images awaiting upload through DeepSeek's native composer. */
        public final List<z4.Attachment> inputImages;
        public final ToolPlan toolPlan;
        public final String previousResponseId;
        public final boolean responsesApi;
        /** Opaque, hashed client conversation identity (for example Claude Code's session UUID). */
        public final String clientSessionScope;
        public final String nativeConversationId;
        public final Integer nativeParentMessageId;
        /** Canonical OpenAI Responses text.format requested by the caller, or null for text. */
        public final JSONObject outputTextFormat;
        public final long deadlineAtMs;
        public final Map<String, String> knownToolCalls;
        public final Set<String> completedToolCalls;
        public final Set<String> repeatableCompletedToolCalls;

        public CompletionRequest(String requestId, String requestedModel, String nativeModel,
                          String basePrompt, String prompt, boolean reasoning, boolean search,
                          int maxOutputTokens, ToolPlan toolPlan,
                          String previousResponseId, boolean responsesApi) {
            this(requestId, requestedModel, nativeModel, basePrompt, prompt, reasoning, search,
                    maxOutputTokens, toolPlan, previousResponseId, responsesApi,
                    System.currentTimeMillis() + COMPLETION_REQUEST_BUDGET_MS,
                    Collections.<String, String>emptyMap(),
                    Collections.<String>emptySet(),
                    Collections.<String>emptySet(), null, null, null, null,
                    Collections.<String>emptyList(),
                    Collections.<z4.Attachment>emptyList());
        }

        public CompletionRequest(String requestId, String requestedModel, String nativeModel,
                          String basePrompt, String prompt, boolean reasoning, boolean search,
                          int maxOutputTokens, ToolPlan toolPlan,
                          String previousResponseId, boolean responsesApi, long deadlineAtMs,
                          Map<String, String> knownToolCalls,
                          Set<String> completedToolCalls,
                          Set<String> repeatableCompletedToolCalls,
                          String clientSessionScope, JSONObject outputTextFormat,
                          String nativeConversationId, Integer nativeParentMessageId,
                          List<String> nativeFileIds,
                          List<z4.Attachment> inputImages) {
            this.requestId = requestId;
            this.requestedModel = requestedModel;
            this.nativeModel = nativeModel;
            this.basePrompt = basePrompt;
            this.prompt = prompt;
            this.reasoning = reasoning;
            this.search = search;
            this.maxOutputTokens = maxOutputTokens;
            this.toolPlan = toolPlan;
            this.previousResponseId = previousResponseId;
            this.responsesApi = responsesApi;
            this.clientSessionScope = clientSessionScope;
            this.outputTextFormat = outputTextFormat;
            this.nativeConversationId = nativeConversationId;
            this.nativeParentMessageId = nativeParentMessageId;
            this.nativeFileIds = nativeFileIds == null || nativeFileIds.isEmpty()
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(nativeFileIds));
            this.inputImages = inputImages == null || inputImages.isEmpty()
                    ? Collections.<z4.Attachment>emptyList()
                    : Collections.unmodifiableList(
                            new ArrayList<z4.Attachment>(inputImages));
            this.deadlineAtMs = deadlineAtMs;
            this.knownToolCalls = knownToolCalls == null || knownToolCalls.isEmpty()
                    ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new HashMap<String, String>(knownToolCalls));
            this.completedToolCalls = completedToolCalls == null || completedToolCalls.isEmpty()
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<String>(completedToolCalls));
            this.repeatableCompletedToolCalls = repeatableCompletedToolCalls == null
                    || repeatableCompletedToolCalls.isEmpty()
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(
                            new HashSet<String>(repeatableCompletedToolCalls));
        }

        public CompletionRequest withPrompt(String replacement) {
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    replacement, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, inputImages);
        }

        public CompletionRequest withReasoning(boolean enabled) {
            if (reasoning == enabled) return this;
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, enabled, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, inputImages);
        }

        /** 专家模型繁忙时切到快速模式 (nativeModel=default)。 */
        public CompletionRequest withNativeModel(String model) {
            if (model == null || model.equals(nativeModel)) return this;
            return new CompletionRequest(requestId, requestedModel, model, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, inputImages);
        }

        public CompletionRequest withToolHistory(ToolHistory history) {
            if (history == null) return this;
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    history.knownCalls, history.completedCalls, history.repeatableCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, inputImages);
        }

        public CompletionRequest withClientSessionScope(String scope) {
            String normalized = scope == null || scope.length() == 0 ? null : scope;
            if (normalized == null ? clientSessionScope == null
                    : normalized.equals(clientSessionScope)) return this;
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    normalized, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, inputImages);
        }

        public CompletionRequest withOutputTextFormat(JSONObject format) {
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, format,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, inputImages);
        }

        public CompletionRequest withNativeConversation(String sid, Integer parentMessageId) {
            String normalized = sid == null || sid.length() == 0 ? null : sid;
            Integer parent = parentMessageId != null && parentMessageId.intValue() > 0
                    ? parentMessageId : null;
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat, normalized, parent,
                    nativeFileIds, inputImages);
        }

        public CompletionRequest withPromptFiles(String replacement, List<String> fileIds) {
            ArrayList<String> merged = new ArrayList<String>(nativeFileIds);
            if (fileIds != null) {
                for (String fileId : fileIds) {
                    if (fileId != null && fileId.length() > 0 && !merged.contains(fileId)) {
                        merged.add(fileId);
                    }
                }
            }
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    replacement, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, merged, inputImages);
        }

        public CompletionRequest withInputImages(List<z4.Attachment> images) {
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, nativeFileIds, images);
        }

        public CompletionRequest withUploadedImages(List<String> fileIds) {
            ArrayList<String> merged = new ArrayList<String>(nativeFileIds);
            if (fileIds != null) {
                for (String fileId : fileIds) {
                    if (fileId != null && fileId.length() > 0 && !merged.contains(fileId)) {
                        merged.add(fileId);
                    }
                }
            }
            return new CompletionRequest(requestId, requestedModel, nativeModel, basePrompt,
                    prompt, reasoning, search, maxOutputTokens, toolPlan,
                    previousResponseId, responsesApi, deadlineAtMs,
                    knownToolCalls, completedToolCalls, repeatableCompletedToolCalls,
                    clientSessionScope, outputTextFormat,
                    nativeConversationId, nativeParentMessageId, merged,
                    Collections.<z4.Attachment>emptyList());
        }

        public boolean toolsActive() {
            return toolPlan != null && toolPlan.active();
        }

        public boolean auxiliary() {
            if (requestedModel == null) return false;
            String lower = requestedModel.toLowerCase(Locale.US);
            return lower.equals("deepseek-aux") || lower.startsWith("deepseek-aux-");
        }

        /** Historical UI/main-turn classifier retained for compatibility tests. */
        public boolean interactiveAgent() {
            return toolsActive() && !auxiliary();
        }

        /** Main Claude turn or a tool-bearing Task/subagent turn. */
        public boolean agentic() {
            return toolsActive();
        }
    }

    public static final class CompletionResult {
        public final String text;
        public final String reasoning;
        public final String finishReason;
        public final List<?> toolCalls;

        public CompletionResult(String text, String reasoning, String finishReason) {
            this(text, reasoning, finishReason, Collections.emptyList());
        }

        public CompletionResult(String text, String reasoning, String finishReason, List<?> toolCalls) {
            this.text = text == null ? "" : text;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.finishReason = finishReason == null ? "stop" : finishReason;
            this.toolCalls = toolCalls == null ? Collections.emptyList() : toolCalls;
        }

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }

    public static class GatewayException extends Exception {
        public final int status;
        public final String code;
        public final String type;

        public GatewayException(int status, String code, String message) {
            this(status, code, "invalid_request_error", message);
        }

        public GatewayException(int status, String code, String type, String message) {
            super(message);
            this.status = status;
            this.code = code;
            this.type = type;
        }
    }

    public static final class ToolHistory {
        public final Map<String, String> knownCalls = new HashMap<String, String>();
        public final Set<String> completedCalls = new HashSet<String>();
        public final Set<String> repeatableCalls = new HashSet<String>();

        public ToolHistory() {}

        public ToolHistory(Map<String, String> knownCalls, Set<String> completedCalls) {
            if (knownCalls != null) this.knownCalls.putAll(knownCalls);
            if (completedCalls != null) this.completedCalls.addAll(completedCalls);
        }
    }

    /** Result of classifying an upstream error message; shared with the encrypted payload. */
    public static final class ErrorClass {
        public int status = 502;
        public String code = "upstream_rejected";
        public String type = "server_error";
        public String error = "";
    }
}
