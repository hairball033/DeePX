package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话转接 (conversation relay): when enabled, native DeepSeek chat messages are routed to a
 * user-configured OpenAI/Anthropic-compatible endpoint instead of DeepSeek's own backend.
 * Supports SSE streaming with reasoning extraction so the host thinking panel is fed correctly.
 */
final class z17 {
    private static final String DIR = "/data/data/com.deepseek.chat/files";
    private static final String ENABLED_FILE = DIR + "/deekseep_chat_proxy_enabled";
    private static final String CONFIG_FILE = DIR + "/deekseep_chat_proxy_config.json";
    static final String FORMAT_OPENAI = "openai";
    static final String FORMAT_ANTHROPIC = "anthropic";

    private static volatile JSONObject cache;

    static final class RelayResult {
        final String reasoning;
        final String content;
        final String error;
        final boolean success;

        RelayResult(String reasoning, String content) {
            this.reasoning = reasoning == null ? "" : reasoning.trim();
            this.content = content == null ? "" : content.trim();
            this.error = null;
            this.success = true;
        }

        RelayResult(String error) {
            this.reasoning = "";
            this.content = "";
            this.error = error;
            this.success = false;
        }
    }

    private z17() {}

    static boolean isEnabled() {
        return new File(ENABLED_FILE).isFile();
    }

    static void setEnabled(boolean on) {
        File flag = new File(ENABLED_FILE);
        try {
            if (on) {
                if (!flag.exists()) {
                    FileOutputStream out = new FileOutputStream(flag, false);
                    try { out.write("1\n".getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
                    finally { out.close(); }
                }
            } else if (flag.exists()) {
                flag.delete();
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized JSONObject config() {
        if (cache != null) return cache;
        try {
            File f = new File(CONFIG_FILE);
            if (!f.isFile()) { cache = new JSONObject(); return cache; }
            byte[] bytes = new byte[(int) f.length()];
            InputStream in = new FileInputStream(f);
            try {
                int off = 0;
                while (off < bytes.length) {
                    int n = in.read(bytes, off, bytes.length - off);
                    if (n < 0) break;
                    off += n;
                }
            } finally { in.close(); }
            cache = new JSONObject(new String(bytes, 0, bytes.length, StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            cache = new JSONObject();
        }
        return cache;
    }

    private static String get(String key) {
        return config().optString(key, "").trim();
    }

    static String apiKey() { return get("api_key"); }
    static String baseUrl() { return get("base_url"); }
    static String model() { return get("model"); }

    static String format() {
        String v = get("format");
        return FORMAT_ANTHROPIC.equals(v) ? FORMAT_ANTHROPIC : FORMAT_OPENAI;
    }

    static synchronized void save(String apiKey, String format, String baseUrl, String model) {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("api_key", apiKey == null ? "" : apiKey.trim());
            cfg.put("format", FORMAT_ANTHROPIC.equals(format) ? FORMAT_ANTHROPIC : FORMAT_OPENAI);
            cfg.put("base_url", baseUrl == null ? "" : baseUrl.trim());
            cfg.put("model", model == null ? "" : model.trim());
            File f = new File(CONFIG_FILE);
            File tmp = new File(CONFIG_FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp, false);
            try { out.write(cfg.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
            finally { out.close(); }
            if (f.exists()) f.delete();
            tmp.renameTo(f);
            cache = cfg;
        } catch (Throwable ignored) {}
    }

    /** Fetches the model id list from {@code baseUrl}/v1/models using the configured API key. */
    static List<String> fetchModels(String apiKey, String format, String baseUrl)
            throws Exception {
        String endpoint = normalize(baseUrl) + "/v1/models";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("Accept", "application/json");
        if (FORMAT_ANTHROPIC.equals(format)) {
            conn.setRequestProperty("x-api-key", apiKey == null ? "" : apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else {
            conn.setRequestProperty("Authorization", "Bearer " + (apiKey == null ? "" : apiKey));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            String body = readStream(conn.getErrorStream());
            conn.disconnect();
            throw new java.io.IOException("HTTP " + code
                    + (body.length() == 0 ? "" : ": " + body.substring(0, Math.min(200, body.length()))));
        }
        String body = readStream(conn.getInputStream());
        conn.disconnect();
        JSONObject json = new JSONObject(body);
        JSONArray data = json.optJSONArray("data");
        ArrayList<String> models = new ArrayList<String>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                String id = data.optJSONObject(i).optString("id", "").trim();
                if (id.length() > 0 && !models.contains(id)) models.add(id);
            }
        }
        return models;
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null) return "";
        String v = baseUrl.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    /**
     * Sends one user message to the configured endpoint via SSE streaming and returns both
     * reasoning and text content. Throws IOException when the endpoint rejects the request.
     */
    static RelayResult relay(String prompt) {
        if (prompt == null || prompt.length() == 0) return new RelayResult("");
        String baseUrl = normalize(baseUrl());
        String key = apiKey();
        String model = model();
        boolean anthropic = FORMAT_ANTHROPIC.equals(format());
        String endpoint = anthropic ? baseUrl + "/v1/messages"
                : baseUrl + "/v1/chat/completions";
        try {
            JSONObject root = new JSONObject();
            root.put("model", model.length() == 0 ? "default" : model);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            root.put("messages", messages);
            root.put("stream", true);
            if (anthropic) root.put("max_tokens", 8192);
            String body = root.toString();

            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            if (anthropic) {
                conn.setRequestProperty("x-api-key", key);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                conn.setRequestProperty("Authorization", "Bearer " + key);
            }
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                out.close();
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                String errBody = readStream(conn.getErrorStream());
                conn.disconnect();
                return new RelayResult("HTTP " + code
                        + (errBody.length() == 0 ? ""
                        : ": " + errBody.substring(0, Math.min(200, errBody.length()))));
            }
            return anthropic ? parseAnthropicStream(conn) : parseOpenAIStream(conn);
        } catch (Throwable t) {
            String msg = t.getMessage();
            return new RelayResult(msg != null ? msg : t.getClass().getSimpleName());
        }
    }

    private static RelayResult parseOpenAIStream(HttpURLConnection conn) throws Exception {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObject chunk = new JSONObject(data);
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject delta = choices.optJSONObject(0).optJSONObject("delta");
                        if (delta != null) {
                            String rc = delta.optString("reasoning_content", "");
                            if (rc.length() > 0) reasoning.append(rc);
                            String c = delta.optString("content", "");
                            if (c.length() > 0) content.append(c);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } finally {
            try { reader.close(); } catch (Throwable ignored) {}
            conn.disconnect();
        }
        return new RelayResult(reasoning.toString(), content.toString());
    }

    private static RelayResult parseAnthropicStream(HttpURLConnection conn) throws Exception {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                try {
                    JSONObject event = new JSONObject(data);
                    String type = event.optString("type", "");
                    if ("content_block_delta".equals(type)) {
                        JSONObject delta = event.optJSONObject("delta");
                        if (delta != null) {
                            String dt = delta.optString("type", "");
                            if ("thinking_delta".equals(dt)) {
                                String think = delta.optString("thinking", "");
                                if (think.length() > 0) reasoning.append(think);
                            } else if ("text_delta".equals(dt)) {
                                String text = delta.optString("text", "");
                                if (text.length() > 0) content.append(text);
                            }
                        }
                    } else if ("content_block_start".equals(type)) {
                        JSONObject block = event.optJSONObject("content_block");
                        if (block != null) {
                            String bt = block.optString("type", "");
                            if ("thinking".equals(bt)) {
                                String think = block.optString("thinking", "");
                                if (think.length() > 0) reasoning.append(think);
                            } else if ("text".equals(bt)) {
                                String text = block.optString("text", "");
                                if (text.length() > 0) content.append(text);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } finally {
            try { reader.close(); } catch (Throwable ignored) {}
            conn.disconnect();
        }
        return new RelayResult(reasoning.toString(), content.toString());
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) if (n > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}