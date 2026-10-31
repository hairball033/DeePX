package com.smallhairball.deepx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict bounded file blocks emitted by the optional dual-chat workspace. */
final class ModelFileOutput {
    static final String PROMPT = "\n\nWhen you need to deliver a file, emit exactly one Markdown file fence:\n"
            + "```file name=\"filename.ext\"\n"
            + "file contents\n```\n"
            + "Supported: html, txt, sh, py, js, json, md, css, java, kt, xml, c, h, yaml. "
            + "Do not use this format unless a real downloadable file helps.";
    private static final int MAX_FILES = 4;
    private static final int MAX_FILE_CHARS = 512 * 1024;
    private static final int MAX_TOTAL_CHARS = 1024 * 1024;
    private static final Pattern BLOCK = Pattern.compile(
            "(?s)<<<DEEKSEEP_FILE\\s+name=\"([^\"]{1,96})\">{2,3}\\s*\\n?"
                    + "(.*?)\\n?<<<END_DEEKSEEP_FILE>>>");
    private static final Pattern STREAM_HEADER = Pattern.compile(
            "^<<<DEEKSEEP_FILE\\s+name=\"([^\"]{1,96})\">{2,3}$");
    private static final Pattern FENCE_OPEN = Pattern.compile(
            "^```file\\s+(?:name|filename)=\"([^\"]{1,96})\"",
            Pattern.CASE_INSENSITIVE);

    static final class FileItem {
        final String name;
        final String content;
        FileItem(String name, String content) { this.name = name; this.content = content; }
        boolean html() { return name.toLowerCase(Locale.US).endsWith(".html"); }
    }

    static final class Parsed {
        final String visibleText;
        final List<FileItem> files;
        Parsed(String visibleText, List<FileItem> files) {
            this.visibleText = visibleText;
            this.files = files;
        }
    }

    static final class StreamingFile {
        final String name;
        final String content;
        final boolean complete;

        StreamingFile(String name, String content, boolean complete) {
            this.name = name;
            this.content = content;
            this.complete = complete;
        }
    }

    static final class StreamingParsed {
        final String visibleText;
        final List<StreamingFile> files;

        StreamingParsed(String visibleText, List<StreamingFile> files) {
            this.visibleText = visibleText;
            this.files = files;
        }
    }

    /**
     * Reconstructs one raw model stream after the visible State has already been replaced by its
     * attachment presentation. Both DeepSeek's fragment renderer and Markdown renderer read the
     * same mutable State; using this shared accumulator prevents the presentation Markdown from
     * being mistaken for a new model response on the second boundary.
     */
    static final class StreamingAccumulator {
        private String raw = "";
        private String visible = "";
        private StreamingParsed latest = new StreamingParsed(
                "", Collections.<StreamingFile>emptyList());
        private boolean initialized;

        StreamingParsed update(String hostText) {
            String host = hostText == null ? "" : hostText;
            if (initialized && (host.equals(visible) || host.equals(raw))) {
                return latest;
            }
            if (initialized && host.startsWith(visible)) {
                raw += host.substring(visible.length());
            } else {
                raw = host;
            }
            latest = parseStreaming(raw);
            visible = latest.visibleText;
            initialized = true;
            return latest;
        }

        StreamingParsed latest() {
            return latest;
        }
    }

    private ModelFileOutput() {}

    static Parsed parse(String raw) {
        String source = raw == null ? "" : raw;
        Matcher matcher = BLOCK.matcher(source);
        StringBuffer visible = new StringBuffer();
        ArrayList<FileItem> files = new ArrayList<FileItem>();
        int total = 0;
        while (matcher.find()) {
            String name = safeName(matcher.group(1));
            String content = matcher.group(2) == null ? "" : matcher.group(2);
            boolean accepted = files.size() < MAX_FILES && name.length() > 0
                    && content.length() <= MAX_FILE_CHARS
                    && total + content.length() <= MAX_TOTAL_CHARS;
            if (accepted) {
                files.add(new FileItem(name, content));
                total += content.length();
                matcher.appendReplacement(visible, "");
            } else {
                matcher.appendReplacement(visible, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(visible);
        return new Parsed(visible.toString().trim(),
                Collections.unmodifiableList(files));
    }

    /**
     * Incremental parser used while the assistant is still streaming.  A valid opening header is
     * removed immediately and exposed as an incomplete file, so UI can create a "generating"
     * card before the closing marker arrives.  The private body and both markers never enter the
     * ordinary message text.
     */
    static StreamingParsed parseStreaming(String raw) {
        String source = raw == null ? "" : raw;
        StreamingParsed marker = parseMarkerStreaming(source);
        if (!marker.files.isEmpty() || containsMarkerStartOrPrefix(source)) return marker;
        return parseFileFenceStreaming(source);
    }

    private static StreamingParsed parseMarkerStreaming(String source) {
        final String start = "<<<DEEKSEEP_FILE";
        final String end = "<<<END_DEEKSEEP_FILE>>>";
        StringBuilder visible = new StringBuilder(source.length());
        ArrayList<StreamingFile> files = new ArrayList<StreamingFile>();
        int total = 0;
        int cursor = 0;
        while (cursor < source.length()) {
            int open = source.indexOf(start, cursor);
            if (open < 0) {
                int partial = trailingMarkerPrefix(source, cursor, start);
                if (partial < 0) visible.append(source, cursor, source.length());
                else visible.append(source, cursor, partial);
                break;
            }
            visible.append(source, cursor, open);
            int lineEnd = source.indexOf('\n', open + start.length());
            if (lineEnd < 0) break;
            int headerEnd = lineEnd;
            if (headerEnd > open && source.charAt(headerEnd - 1) == '\r') headerEnd--;
            String header = source.substring(open, headerEnd);
            Matcher headerMatcher = STREAM_HEADER.matcher(header);
            if (!headerMatcher.matches()) {
                visible.append(source.charAt(open));
                cursor = open + 1;
                continue;
            }
            String name = safeName(headerMatcher.group(1));
            if (name.length() == 0 || files.size() >= MAX_FILES) {
                visible.append(header);
                cursor = lineEnd + 1;
                continue;
            }
            int bodyStart = lineEnd + 1;
            int close = source.indexOf(end, bodyStart);
            boolean complete = close >= 0;
            int bodyEnd = complete ? close : source.length();
            String content = source.substring(bodyStart, bodyEnd);
            if (complete && content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
                if (content.endsWith("\r")) content = content.substring(0, content.length() - 1);
            }
            if (content.length() <= MAX_FILE_CHARS
                    && total + content.length() <= MAX_TOTAL_CHARS) {
                files.add(new StreamingFile(name, content, complete));
                total += content.length();
            }
            if (!complete) break;
            cursor = close + end.length();
        }
        return new StreamingParsed(withInlineFileCards(visible.toString(), files),
                Collections.unmodifiableList(files));
    }

    /**
     * Handles an explicit Markdown fence before DeepSeek's native Markdown parser consumes it.
     * Requiring the reserved {@code file name="..."} info string prevents normal code examples
     * from turning into downloads.
     */
    private static StreamingParsed parseFileFenceStreaming(String source) {
        final String start = "```file";
        StringBuilder visible = new StringBuilder(source.length());
        ArrayList<StreamingFile> files = new ArrayList<StreamingFile>();
        int total = 0;
        int cursor = 0;
        while (cursor < source.length()) {
            int open = source.indexOf(start, cursor);
            if (open < 0) {
                int partial = trailingMarkerPrefix(source, cursor, start);
                if (partial < 0) visible.append(source, cursor, source.length());
                else visible.append(source, cursor, partial);
                break;
            }
            visible.append(source, cursor, open);
            Matcher headerMatcher = FENCE_OPEN.matcher(source.substring(open));
            if (!headerMatcher.find()) {
                visible.append(source.charAt(open));
                cursor = open + 1;
                continue;
            }
            String name = safeName(headerMatcher.group(1));
            int afterHeader = open + headerMatcher.end();
            int bodyStart = afterHeader;
            boolean invalidSeparator = false;
            while (bodyStart < source.length()) {
                char separator = source.charAt(bodyStart);
                if (separator == ' ' || separator == '\t') {
                    bodyStart++;
                    continue;
                }
                if (separator == '\r') {
                    bodyStart++;
                    if (bodyStart < source.length() && source.charAt(bodyStart) == '\n') {
                        bodyStart++;
                    }
                } else if (separator == '\n') {
                    bodyStart++;
                } else if (bodyStart == afterHeader) {
                    invalidSeparator = true;
                }
                break;
            }
            if (invalidSeparator) {
                visible.append(source.charAt(open));
                cursor = open + 1;
                continue;
            }
            if (name.length() == 0 || files.size() >= MAX_FILES) {
                visible.append(source, open, bodyStart);
                cursor = bodyStart;
                continue;
            }
            int close = findFenceClose(source, bodyStart);
            boolean complete = close >= 0;
            int bodyEnd = complete ? close : source.length();
            String content = source.substring(bodyStart, bodyEnd);
            if (complete && content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
                if (content.endsWith("\r")) content = content.substring(0,
                        content.length() - 1);
            }
            if (content.length() <= MAX_FILE_CHARS
                    && total + content.length() <= MAX_TOTAL_CHARS) {
                files.add(new StreamingFile(name, content, complete));
                total += content.length();
            }
            if (!complete) break;
            cursor = close + 3;
        }
        return new StreamingParsed(withInlineFileCards(visible.toString(), files),
                Collections.unmodifiableList(files));
    }

    /**
     * A stable native-Markdown attachment row. It lives inside the assistant message and thus
     * scrolls with the conversation; the private file body never enters visible Markdown.
     */
    private static String withInlineFileCards(
            String visible, List<StreamingFile> files) {
        // Presentation is injected by NativeModelFileCardRenderer through DeepSeek's own hz0.e
        // upload-attachment composable. Keeping a Markdown imitation here would render a second
        // card and would also feed generated presentation text back into the streaming parser.
        return visible == null ? "" : visible;
    }

    private static int findFenceClose(String source, int from) {
        int at = source.indexOf("```", from);
        while (at >= 0) {
            boolean lineStart = at == from || source.charAt(at - 1) == '\n';
            int after = at + 3;
            boolean lineEnd = after == source.length() || source.charAt(after) == '\n'
                    || source.charAt(after) == '\r';
            if (lineStart && lineEnd) return at;
            at = source.indexOf("```", at + 3);
        }
        return -1;
    }

    private static boolean containsMarkerStartOrPrefix(String source) {
        final String marker = "<<<DEEKSEEP_FILE";
        if (source.indexOf(marker) >= 0) return true;
        int partial = trailingMarkerPrefix(source, 0, marker);
        return partial >= 0 && source.length() - partial >= 4;
    }

    private static int trailingMarkerPrefix(String source, int from, String marker) {
        int maximum = Math.min(marker.length() - 1, source.length() - from);
        for (int length = maximum; length > 0; length--) {
            int at = source.length() - length;
            if (at >= from && source.regionMatches(at, marker, 0, length)) return at;
        }
        return -1;
    }

    private static String safeName(String input) {
        String value = input == null ? "" : input.trim();
        if (value.contains("/") || value.contains("\\") || value.contains("..")
                || value.startsWith(".") || value.length() > 96) return "";
        int dot = value.lastIndexOf('.');
        if (dot <= 0 || dot == value.length() - 1) return "";
        String ext = value.substring(dot + 1).toLowerCase(Locale.US);
        if (!("html".equals(ext) || "txt".equals(ext) || "sh".equals(ext)
                || "py".equals(ext) || "js".equals(ext) || "json".equals(ext)
                || "md".equals(ext) || "css".equals(ext) || "java".equals(ext)
                || "kt".equals(ext) || "xml".equals(ext) || "c".equals(ext)
                || "h".equals(ext) || "yaml".equals(ext) || "yml".equals(ext))) return "";
        return value.replaceAll("[^A-Za-z0-9._ -]", "_");
    }
}
