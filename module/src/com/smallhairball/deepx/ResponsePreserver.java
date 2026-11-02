package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable counterpart of the live CONTENT_FILTER guards.
 *
 * The server history endpoint can return the already-finalised template on the next process start,
 * when the original mv object no longer exists.  We therefore serialise only messages for which a
 * real filter event was observed, using DeepSeek's own kv serializer, and restore that exact object
 * before history rendering or persistence.  Files remain in DeepSeek's private files directory.
 */
final class ResponsePreserver {
    private static final String DEFAULT_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_preserved_responses";
    private static final String TEST_DIR_PROPERTY = "deekseep.response_preserver_dir";
    private static final int SCHEMA = 1;
    private static final int MAX_HOST_JSON = 4 * 1024 * 1024;
    private static final Object IO_LOCK = new Object();

    private ResponsePreserver() {}

    static boolean isFilteredRecord(String status, String quasiStatus, String fragments) {
        if (containsFilterStatus(status) || containsFilterStatus(quasiStatus)) return true;
        if (fragments == null || fragments.length() == 0) return false;
        try {
            JSONArray array = new JSONArray(fragments);
            for (int i = 0; i < array.length(); i++) {
                JSONObject fragment = array.optJSONObject(i);
                if (fragment != null
                        && "TEMPLATE_RESPONSE".equals(fragment.optString("type"))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static boolean isFilteredHostMessage(Object message) {
        if (message == null) return false;
        Object row = call(message, "O");
        return isFilteredRecord(callString(message, "D"), callString(message, "x"),
                asString(field(row, "l")));
    }

    /** Save the full static kv only after Main has observed an actual filter event. */
    static boolean saveHostMessage(ClassLoader cl, String sid, Object message) {
        if (!validSid(sid) || message == null) return false;
        Integer messageId = callInteger(message, "u");
        if (messageId == null || messageId.intValue() <= 0
                || !"ASSISTANT".equals(callString(message, "A"))
                || isFilteredHostMessage(message)) return false;

        Object row = call(message, "O");
        String fragments = asString(field(row, "l"));
        int score = originalContentScore(fragments);
        if (score <= 0) return false;
        int stateQuality = stateQuality(callString(message, "D"), callString(message, "x"));

        Object staticMessage = call(message, "a");
        if (staticMessage == null) return false;
        forceRegenerateAllowedV241(staticMessage);
        String hostJson = encodeHostMessage(cl, staticMessage);
        if (hostJson == null || hostJson.length() == 0 || hostJson.length() > MAX_HOST_JSON) {
            return false;
        }

        synchronized (IO_LOCK) {
            try {
                File dir = storageDir();
                if (!dir.exists() && !dir.mkdirs()) return false;
                File target = recordFile(dir, sid, messageId.intValue());
                JSONObject old = readRecord(target);
                if (old != null && old.optString("host_message", "").length() > 0) {
                    int oldScore = old.optInt("score", 0);
                    int oldStateQuality = old.optInt("state_quality", 0);
                    if (oldScore > score
                            || (oldScore == score && oldStateQuality >= stateQuality)) return true;
                }

                JSONObject root = new JSONObject();
                root.put("schema", SCHEMA);
                root.put("sid", sid);
                root.put("message_id", messageId.intValue());
                root.put("score", score);
                root.put("state_quality", stateQuality);
                root.put("saved_at", System.currentTimeMillis());
                root.put("host_message", hostJson);
                if (old != null) {
                    long filteredAt = old.optLong("filtered_at", 0L);
                    long relayedAt = old.optLong("relayed_at", 0L);
                    if (filteredAt > 0L) root.put("filtered_at", filteredAt);
                    if (relayedAt > 0L) root.put("relayed_at", relayedAt);
                }
                File temp = new File(dir, target.getName() + ".tmp");
                FileWriter writer = new FileWriter(temp, false);
                writer.write(root.toString());
                writer.close();
                if (target.exists() && !target.delete()) {
                    temp.delete();
                    return false;
                }
                return temp.renameTo(target);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /**
     * Exact 2.4.1 context-only fallback. Some code257 builds expose the live answer before their
     * static-message serializer can encode it. Keep only the response text so the next ordinary
     * request can retain context; full host-message restoration continues to use host_message.
     */
    static boolean saveFilteredResponseTextV241(String sid, Object message) {
        if (!HostCompat.isV241() || !validSid(sid) || message == null) return false;
        Integer messageId = callInteger(message, "u");
        if (messageId == null || messageId.intValue() <= 0
                || !"ASSISTANT".equals(callString(message, "A"))) return false;
        Object row = call(message, "O");
        String fragments = asString(field(row, "l"));
        String text = responseText(fragments, MAX_HOST_JSON);
        if (text.length() == 0 || isFilteredRecord(
                callString(message, "D"), callString(message, "x"), fragments)) return false;
        synchronized (IO_LOCK) {
            try {
                File dir = storageDir();
                if (!dir.exists() && !dir.mkdirs()) return false;
                File target = recordFile(dir, sid, messageId.intValue());
                JSONObject root = readRecord(target);
                if (root == null) root = new JSONObject();
                root.put("schema", SCHEMA);
                root.put("sid", sid);
                root.put("message_id", messageId.intValue());
                root.put("score", text.length());
                root.put("state_quality", stateQuality(
                        callString(message, "D"), callString(message, "x")));
                root.put("saved_at", System.currentTimeMillis());
                root.put("filtered_at", System.currentTimeMillis());
                root.put("response_text", text);
                root.remove("relayed_at");
                return writeRecord(target, root);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /** Returns a verified preserved kv only when the incoming object is a filter replacement. */
    static Object restoreHostMessage(ClassLoader cl, String sid, Object incoming) {
        if (!validSid(sid) || incoming == null || !isFilteredHostMessage(incoming)) return null;
        Integer messageId = callInteger(incoming, "u");
        if (messageId == null || messageId.intValue() <= 0) return null;
        JSONObject record;
        synchronized (IO_LOCK) {
            record = readRecord(recordFile(storageDir(), sid, messageId.intValue()));
        }
        if (!validRecord(record, sid, messageId.intValue())) return null;
        String hostJson = record.optString("host_message", "");
        // code257 can observe the live answer one hook boundary before DeepSeek exposes a
        // serialisable static kv. That exact path records response_text as a durable fallback.
        // On cold-start sync the host then supplies only the CONTENT_FILTER replacement; do not
        // discard the already-proven fallback just because a complete kv was unavailable then.
        if (hostJson.length() == 0) {
            return restoreTextFallbackV241(sid, messageId.intValue(), incoming, record);
        }
        if (hostJson.length() > MAX_HOST_JSON) return null;
        Object restored = decodeHostMessage(cl, hostJson);
        promoteRestoredMessage(restored);
        forceRegenerateAllowedV241(restored);
        if (restored == null
                || !messageId.equals(callInteger(restored, "u"))
                || !"ASSISTANT".equals(callString(restored, "A"))
                || isFilteredHostMessage(restored)) return null;
        Object row = call(restored, "O");
        if (originalContentScore(asString(field(row, "l"))) <= 0) return null;
        markFiltered(sid, messageId.intValue());
        return restored;
    }

    /**
     * Exact code257 cold-start adapter for an answer captured before its static-message encoder
     * became available. Reuse the incoming native message identity, replace only its filtered
     * fragment payload with the saved response text, and promote it to the ordinary terminal
     * state. Other host branches keep the full-kv-only restoration contract unchanged.
     */
    private static Object restoreTextFallbackV241(String sid, int messageId, Object incoming,
                                                   JSONObject record) {
        if (!HostCompat.isV241()) return null;
        String text = record.optString("response_text", "");
        if (text.length() == 0 || text.length() > MAX_HOST_JSON) return null;
        Object row = call(incoming, "O");
        if (row == null) return null;
        String fragments;
        try {
            fragments = new JSONArray().put(new JSONObject()
                    .put("type", "RESPONSE")
                    .put("id", 3)
                    .put("content", text)
                    .put("references", new JSONArray())
                    .put("stage_id", 1)).toString();
        } catch (Throwable ignored) {
            return null;
        }
        if (!setField(row, "l", fragments)) return null;
        // sl8.e is the persisted status field on code257. The live message status is promoted
        // below through its verified HostCompat setter, so both online-history and repository
        // write paths see one completed assistant message.
        setField(row, "e", "FINISHED");
        promoteRestoredMessage(incoming);
        clearFilteredStateV241(incoming);
        forceRegenerateAllowedV241(incoming);
        if (isFilteredHostMessage(incoming)
                || !"ASSISTANT".equals(callString(incoming, "A"))) return null;
        markFiltered(sid, messageId);
        return incoming;
    }

    private static void clearFilteredStateV241(Object message) {
        if (!HostCompat.isV241() || message == null) return;
        for (Class<?> type = message.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(message);
                    if (value instanceof String && ((String) value).contains("CONTENT_FILTER")) {
                        field.set(message, "FINISHED");
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Marks only responses proven to have received a CONTENT_FILTER replacement. */
    static boolean markFiltered(String sid, int messageId) {
        if (!validSid(sid) || messageId <= 0) return false;
        synchronized (IO_LOCK) {
            File target = recordFile(storageDir(), sid, messageId);
            JSONObject record = readRecord(target);
            if (!validRecord(record, sid, messageId)) return false;
            if (record.optLong("filtered_at", 0L) <= 0L) {
                try { record.put("filtered_at", System.currentTimeMillis()); }
                catch (Throwable ignored) { return false; }
            }
            return writeRecord(target, record);
        }
    }

    /**
     * Claims the newest filtered answer once and returns its response text for a hidden system
     * context wrapper on the user's next send. Ordinary proactively snapshotted answers are never
     * eligible, and relayed answers cannot be injected repeatedly.
     */
    static String claimLatestFilteredResponseText(
            ClassLoader cl, String sid, int maximumChars) {
        if (!validSid(sid) || cl == null || maximumChars <= 0) return "";
        synchronized (IO_LOCK) {
            File[] files = storageDir().listFiles();
            if (files == null) return "";
            String prefix = sid + "__";
            JSONObject selected = null;
            File selectedFile = null;
            long selectedAt = Long.MIN_VALUE;
            for (File file : files) {
                if (file == null || !file.isFile()
                        || !file.getName().startsWith(prefix)
                        || !file.getName().endsWith(".json")) continue;
                JSONObject record = readRecord(file);
                int messageId = record == null ? -1 : record.optInt("message_id", -1);
                if (!validRecord(record, sid, messageId)) continue;
                long filteredAt = record.optLong("filtered_at", 0L);
                long relayedAt = record.optLong("relayed_at", 0L);
                if (filteredAt <= 0L || relayedAt >= filteredAt) continue;
                long candidateAt = Math.max(filteredAt, record.optLong("saved_at", 0L));
                if (candidateAt > selectedAt) {
                    selectedAt = candidateAt;
                    selected = record;
                    selectedFile = file;
                }
            }
            if (selected == null || selectedFile == null) return "";
            String text = selected.optString("response_text", "");
            if (text.length() > maximumChars) text = text.substring(0, maximumChars);
            if (text.length() == 0) {
                Object message = decodeHostMessage(cl, selected.optString("host_message", ""));
                Object row = call(message, "O");
                text = responseText(asString(field(row, "l")), maximumChars);
            }
            if (text.length() == 0) return "";
            try { selected.put("relayed_at", System.currentTimeMillis()); }
            catch (Throwable ignored) { return ""; }
            return writeRecord(selectedFile, selected) ? text : "";
        }
    }

    /**
     * Proactively checkpoint normal assistant messages while the server still returns their real
     * content. 2.3.6 may deliver a later filter replacement as a brand-new static message after
     * the live mutable object has left the session map; waiting for that replacement is then too
     * late to discover the original. Restore remains deliberately narrow and only accepts an
     * incoming CONTENT_FILTER/TEMPLATE_RESPONSE record.
     */
    static int snapshotHistoryResponse(ClassLoader cl, Object response) {
        Object session = field(response, "a");
        String sid = asString(field(session, "a"));
        Object value = field(response, "b");
        if (!validSid(sid) || !(value instanceof List)) return 0;
        int saved = 0;
        for (Object message : (List<?>) value) {
            if (saveHostMessage(cl, sid, message)) saved++;
        }
        return saved;
    }

    /** Replace filtered kv entries before pw0 leaves its constructor hook. */
    static int restoreHistoryResponse(ClassLoader cl, Object response) {
        Object session = field(response, "a");
        String sid = asString(field(session, "a"));
        Object value = field(response, "b");
        if (!validSid(sid) || !(value instanceof List)) return 0;
        List messages = (List) value;
        ArrayList<Object> replacement = null;
        int restored = 0;
        for (int i = 0; i < messages.size(); i++) {
            Object incoming = messages.get(i);
            Object original = restoreHostMessage(cl, sid, incoming);
            if (original == null) continue;
            try {
                messages.set(i, original);
                restored++;
            } catch (Throwable immutable) {
                if (replacement == null) replacement = new ArrayList<Object>(messages);
                replacement.set(i, original);
            }
        }
        if (replacement != null && setField(response, "b", replacement)) {
            for (int i = 0; i < replacement.size(); i++) {
                if (replacement.get(i) != messages.get(i)) restored++;
            }
        }
        return restored;
    }

    /** Replace filtered sl8/rl8 values before gm8/fm8 writes them to WCDB. */
    static int restoreRepositoryRows(ClassLoader cl, String sid, Object rowsValue) {
        if (!validSid(sid) || !(rowsValue instanceof List)) return 0;
        int restored = 0;
        for (Object incoming : (List<?>) rowsValue) {
            Integer messageId = asInteger(field(incoming, "a"));
            String status = asString(field(incoming, "e"));
            String fragments = asString(field(incoming, "l"));
            if (messageId == null || messageId.intValue() <= 0
                    || !"ASSISTANT".equals(asString(field(incoming, "c")))
                    || !isFilteredRecord(status, null, fragments)) continue;
            // code257's cold-start path maps the raw WCDB sl8 row directly into the native
            // message map. A filter may have arrived before the static kv serializer was
            // available, leaving a response_text-only record. Restore that exact, already
            // observed text on the raw row before te(case 8) consumes it; pw0 is not involved
            // in this route. Older hosts retain their complete-kv-only contract below.
            if (HostCompat.isV241() && restoreTextRepositoryRowV241(sid,
                    messageId.intValue(), incoming)) {
                restored++;
                continue;
            }
            Object original = loadPreservedMessage(cl, sid, messageId.intValue());
            Object originalRow = call(original, "O");
            if (originalRow == null
                    || !messageId.equals(asInteger(field(originalRow, "a")))
                    || isFilteredRecord(asString(field(originalRow, "e")), null,
                            asString(field(originalRow, "l")))) continue;
            if (!setField(incoming, "l", field(originalRow, "l"))) continue;
            if (!setField(incoming, "e", field(originalRow, "e"))) continue;
            for (String name : new String[]{"b", "c", "d", "f", "g", "h", "i", "j", "k", "m"}) {
                setField(incoming, name, field(originalRow, name));
            }
            // Exact code257 anti-recall adapter: a preserved response must retain the native
            // regenerate action even when its pre-filter snapshot was taken during a transient
            // host state with ban_regenerate set. Other hosts keep their original row byte path.
            if (HostCompat.isV241()) setField(incoming, "j", Boolean.FALSE);
            restored++;
        }
        return restored;
    }

    /** Exact code257 WCDB-row adapter for a verified response_text-only filter snapshot. */
    private static boolean restoreTextRepositoryRowV241(String sid, int messageId, Object row) {
        if (!HostCompat.isV241() || row == null) return false;
        JSONObject record;
        synchronized (IO_LOCK) {
            record = readRecord(recordFile(storageDir(), sid, messageId));
        }
        if (!validRecord(record, sid, messageId)
                || record.optString("host_message", "").length() > 0) return false;
        String text = record.optString("response_text", "");
        if (text.length() == 0 || text.length() > MAX_HOST_JSON) return false;
        String fragments;
        try {
            fragments = new JSONArray().put(new JSONObject()
                    .put("type", "RESPONSE")
                    .put("id", 3)
                    .put("content", text)
                    .put("references", new JSONArray())
                    .put("stage_id", 1)).toString();
        } catch (Throwable ignored) {
            return false;
        }
        boolean content = setField(row, "l", fragments);
        boolean status = setField(row, "e", "FINISHED");
        if (content) setField(row, "j", Boolean.FALSE);
        return content && status;
    }

    static void forgetSession(String sid) {
        if (!validSid(sid)) return;
        synchronized (IO_LOCK) {
            File[] files = storageDir().listFiles();
            if (files == null) return;
            String prefix = sid + "__";
            for (File file : files) {
                if (file != null && file.isFile() && file.getName().startsWith(prefix)) {
                    file.delete();
                }
            }
        }
    }

    private static Object loadPreservedMessage(ClassLoader cl, String sid, int messageId) {
        JSONObject record;
        synchronized (IO_LOCK) {
            record = readRecord(recordFile(storageDir(), sid, messageId));
        }
        if (!validRecord(record, sid, messageId)) return null;
        Object restored = decodeHostMessage(cl, record.optString("host_message", ""));
        promoteRestoredMessage(restored);
        if (restored == null || messageId != intValue(callInteger(restored, "u"), -1)
                || !"ASSISTANT".equals(callString(restored, "A"))
                || isFilteredHostMessage(restored)) return null;
        Object row = call(restored, "O");
        return originalContentScore(asString(field(row, "l"))) > 0 ? restored : null;
    }

    /**
     * A filter event can be captured while the host row is still WIP.  The response body is real,
     * but replaying that WIP object during cold history sync makes the host hide it as an
     * unfinished/retracted turn.  Only messages already loaded from the private filter-preserve
     * store are promoted; ordinary host messages never enter this method.
     */
    private static void promoteRestoredMessage(Object message) {
        if (message == null || "FINISHED".equals(callString(message, "D"))) return;
        try {
            Method setter = message.getClass().getMethod(
                    HostCompat.messageMethod("S"), String.class);
            setter.setAccessible(true);
            setter.invoke(message, "FINISHED");
        } catch (Throwable ignored) {
            // Older test doubles and a few legacy host builds expose immutable static messages;
            // their already-saved FINISHED records still pass through unchanged.
        }
    }

    private static void forceRegenerateAllowedV241(Object message) {
        if (!HostCompat.isV241() || message == null) return;
        // O() is a serializer-row copy on 2.4.1.  The footer reads the live message flag.
        // jw.d()/k is ban_edit; jw.f()/l is ban_regenerate on exact code257.
        setField(message, "l", Boolean.FALSE);
        Object row = call(message, "O");
        if (row != null) setField(row, "j", Boolean.FALSE);
    }

    private static boolean validRecord(JSONObject record, String sid, int messageId) {
        return record != null && record.optInt("schema", 0) == SCHEMA
                && sid.equals(record.optString("sid", ""))
                && record.optInt("message_id", -1) == messageId;
    }

    private static String encodeHostMessage(ClassLoader cl, Object message) {
        try {
            Object json = staticField(HostCompat.load(cl, "x94"), "a");
            Object serializer = staticField(HostCompat.load(cl, "hv"), "a");
            Method encode = method(json, "c", 2);
            Object value = encode == null ? null : encode.invoke(json, serializer, message);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object decodeHostMessage(ClassLoader cl, String value) {
        if (value == null || value.length() == 0 || value.length() > MAX_HOST_JSON) return null;
        try {
            Object json = staticField(HostCompat.load(cl, "x94"), "a");
            Object serializer = staticField(HostCompat.load(cl, "hv"), "a");
            Method decode = method(json, "b", 2);
            return decode == null ? null : decode.invoke(json, serializer, value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int originalContentScore(String fragments) {
        if (fragments == null || fragments.length() == 0) return 0;
        try {
            JSONArray array = new JSONArray(fragments);
            int score = 0;
            boolean response = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject fragment = array.optJSONObject(i);
                if (fragment == null) continue;
                String type = fragment.optString("type", "");
                if ("TEMPLATE_RESPONSE".equals(type)) return 0;
                String content = fragment.optString("content", "");
                if ("RESPONSE".equals(type) && content.trim().length() > 0) {
                    response = true;
                    score += 1000 + content.length();
                } else if (("THINK".equals(type) || "SEARCH".equals(type))
                        && content.length() > 0) {
                    score += content.length();
                }
            }
            return response ? score : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean containsFilterStatus(String value) {
        return value != null && value.contains("CONTENT_FILTER");
    }

    private static int stateQuality(String status, String quasiStatus) {
        String value = status == null || status.length() == 0 ? quasiStatus : status;
        if (value == null) return 0;
        if (value.contains("FINISHED")) return 3;
        if (value.contains("INTERRUPTED") || value.contains("ERROR")) return 2;
        if (value.contains("STREAMING")) return 1;
        return 0;
    }

    private static File storageDir() {
        String override = System.getProperty(TEST_DIR_PROPERTY);
        return new File(override == null || override.length() == 0 ? DEFAULT_DIR : override);
    }

    private static File recordFile(File dir, String sid, int messageId) {
        return new File(dir, sid + "__" + messageId + ".json");
    }

    private static JSONObject readRecord(File file) {
        if (file == null || !file.isFile() || file.length() <= 0
                || file.length() > MAX_HOST_JSON + 4096L) return null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringBuilder out = new StringBuilder((int) Math.min(file.length(), 8192L));
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (count > 0) out.append(buffer, 0, count);
                if (out.length() > MAX_HOST_JSON + 4096) return null;
            }
            return new JSONObject(out.toString());
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
        }
    }

    private static boolean writeRecord(File target, JSONObject record) {
        if (target == null || record == null) return false;
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        FileWriter writer = null;
        try {
            writer = new FileWriter(temp, false);
            writer.write(record.toString());
            writer.flush();
            writer.close();
            writer = null;
            if (target.exists() && !target.delete()) return false;
            return temp.renameTo(target);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
            if (temp.exists() && !temp.equals(target)) temp.delete();
        }
    }

    private static String responseText(String fragments, int maximumChars) {
        if (fragments == null || fragments.length() == 0) return "";
        try {
            JSONArray array = new JSONArray(fragments);
            StringBuilder output = new StringBuilder();
            for (int index = 0; index < array.length(); index++) {
                JSONObject fragment = array.optJSONObject(index);
                if (fragment == null || !"RESPONSE".equals(
                        fragment.optString("type", ""))) continue;
                String content = fragment.optString("content", "");
                if (content.length() == 0) continue;
                if (output.length() > 0) output.append('\n');
                int remaining = maximumChars - output.length();
                if (remaining <= 0) break;
                output.append(content, 0, Math.min(content.length(), remaining));
            }
            return output.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean validSid(String sid) {
        return sid != null && sid.matches("[0-9a-fA-F-]{36}");
    }

    private static Object staticField(Class<?> type, String name) throws Throwable {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Method method(Object target, String name, int parameters) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (name.equals(method.getName())
                        && method.getParameterTypes().length == parameters) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Object call(Object target, String name) {
        Method method = method(target, HostCompat.instanceMethod(target, name), 0);
        if (method == null) return null;
        try { return method.invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    private static String callString(Object target, String name) {
        Object value = call(target, name);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer callInteger(Object target, String name) {
        return asInteger(call(target, name));
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        name = HostCompat.staticMessageField(target, name);
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean setField(Object target, String name, Object value) {
        if (target == null) return false;
        name = HostCompat.staticMessageField(target, name);
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private static int intValue(Integer value, int fallback) {
        return value == null ? fallback : value.intValue();
    }
}
