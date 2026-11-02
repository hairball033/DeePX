package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Bing HTML search used by the built-in Agent search_web tool. */
final class AgentBingSearch {
    private static final String ENDPOINT = "https://www.bing.com/search?q=";
    private static final String NEWS_ENDPOINT = "https://www.bing.com/news/search?format=rss&q=";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final String FIREFOX_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RESULTS = 10;
    private static final int MAX_PAGES_TO_READ = 6;
    private static final int MAX_PAGE_TEXT = 6000;

    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "(?is)<li\\b[^>]*class\\s*=\\s*([\"'])[^\"']*\\bb_algo\\b[^\"']*\\1[^>]*>(.*?)</li>");
    private static final Pattern TITLE_LINK = Pattern.compile(
            "(?is)<h2\\b[^>]*>.*?<a\\b[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>.*?</h2>");
    private static final Pattern MOBILE_TITLE_LINK = Pattern.compile(
            "(?is)<div\\b[^>]*class\\s*=\\s*([\"'])[^\"']*\\bb_algoheader\\b[^\"']*\\1[^>]*>"
                    + ".*?<a\\b[^>]*href\\s*=\\s*([\"'])(.*?)\\2[^>]*>.*?<h2\\b[^>]*>(.*?)</h2>.*?</a>");
    private static final Pattern CAPTION = Pattern.compile(
            "(?is)<(?:div|p)\\b[^>]*class\\s*=\\s*([\"'])[^\"']*\\bb_caption\\b[^\"']*\\1[^>]*>(.*?)</(?:div|p)>");
    private static final Pattern PARAGRAPH = Pattern.compile(
            "(?is)<p\\b[^>]*>(.*?)</p>");
    private static final Pattern RSS_ITEM = Pattern.compile(
            "(?is)<item>(.*?)</item>");
    private static final Pattern RSS_TITLE = Pattern.compile(
            "(?is)<title>(.*?)</title>");
    private static final Pattern RSS_LINK = Pattern.compile(
            "(?is)<link>(.*?)</link>");
    private static final Pattern RSS_DESCRIPTION = Pattern.compile(
            "(?is)<description>(.*?)</description>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern DOCUMENT_NOISE = Pattern.compile(
            "(?is)<(?:script|style|noscript|svg|canvas|nav|footer|header|form|aside)\\b[^>]*>.*?</(?:script|style|noscript|svg|canvas|nav|footer|header|form|aside)>");
    private static final Pattern ARTICLE = Pattern.compile(
            "(?is)<article\\b[^>]*>(.*?)</article>");
    private static final Pattern MAIN = Pattern.compile(
            "(?is)<main\\b[^>]*>(.*?)</main>");
    private static final Pattern BODY = Pattern.compile(
            "(?is)<body\\b[^>]*>(.*?)</body>");
    private static final Pattern ENTITY = Pattern.compile("&(#(?:x[0-9a-f]+|[0-9]+)|[a-z]+);");

    interface Callback {
        void onResult(SearchResult result);
    }

    static final class Item {
        final String id;
        final int index;
        final String title;
        final String url;
        final String snippet;
        final String siteName;
        final String content;

        Item(String id, int index, String title, String url,
             String snippet, String siteName) {
            this(id, index, title, url, snippet, siteName, "");
        }

        Item(String id, int index, String title, String url,
             String snippet, String siteName, String content) {
            this.id = id;
            this.index = index;
            this.title = title;
            this.url = url;
            this.snippet = snippet;
            this.siteName = siteName;
            this.content = content == null ? "" : content;
        }
    }

    static final class SearchResult {
        final boolean success;
        final String query;
        final List<Item> items;
        final String error;

        SearchResult(boolean success, String query, List<Item> items, String error) {
            this.success = success;
            this.query = query == null ? "" : query;
            this.items = items == null ? new ArrayList<Item>() : items;
            this.error = error == null ? "" : error;
        }

        String toModelJson() {
            try {
                JSONObject root = new JSONObject();
                root.put("query", query);
                root.put("count", items.size());
                root.put("display_summary", "已搜索到 " + items.size() + " 个网页");
                JSONArray pages = new JSONArray();
                JSONArray displayPages = new JSONArray();
                for (Item item : items) {
                    JSONObject page = new JSONObject();
                    page.put("id", item.id);
                    page.put("index", item.index);
                    page.put("title", item.title);
                    page.put("url", item.url);
                    page.put("snippet", item.snippet);
                    page.put("text", item.content.length() > 0
                            ? item.content : item.snippet);
                    page.put("site_name", item.siteName);
                    pages.put(page);
                    displayPages.put(new JSONObject()
                            .put("title", item.title)
                            .put("site_name", item.siteName)
                            .put("url", item.url));
                }
                root.put("results", pages);
                root.put("display_pages", displayPages);
                root.put("citation_format", "[citation,domain](id)");
                return root.toString();
            } catch (Throwable ignored) {
                return "{\"count\":0,\"results\":[]}";
            }
        }

        String detail(boolean chinese) {
            if (!success) {
                return chinese ? "联网搜索暂不可用：" + error
                        : "Web search is unavailable: " + error;
            }
            return chinese ? "已搜索到 " + items.size() + " 个网页"
                    : "Searched " + items.size() + " web pages";
        }
    }

    private AgentBingSearch() {}

    static void searchAsync(final String query, final Callback callback) {
        final AtomicBoolean delivered = new AtomicBoolean(false);
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                SearchResult result;
                try {
                    result = search(query);
                } catch (Throwable error) {
                    result = new SearchResult(false, query,
                            new ArrayList<Item>(), safeMessage(error));
                }
                if (callback != null && delivered.compareAndSet(false, true)) {
                    callback.onResult(result);
                }
            }
        }, "DeePX-Bing-Search");
        worker.setDaemon(true);
        worker.start();
    }

    static SearchResult search(String query) throws Exception {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() == 0) {
            return new SearchResult(false, "", new ArrayList<Item>(), "empty query");
        }
        String encoded = URLEncoder.encode(safeQuery, "UTF-8");
        List<Item> items = new ArrayList<>();
        if (isNewsQuery(safeQuery)) {
            try {
                items = filterRelevant(parseRss(fetch(
                        NEWS_ENDPOINT + encoded, false)), safeQuery);
            } catch (Throwable ignored) {}
            if (items.isEmpty()) {
                String relaxedQuery = relaxNewsQuery(safeQuery);
                if (!relaxedQuery.equals(safeQuery)) {
                    try {
                        items = filterRelevant(parseRss(fetch(
                                NEWS_ENDPOINT + URLEncoder.encode(relaxedQuery, "UTF-8"), false)),
                                relaxedQuery);
                    } catch (Throwable ignored) {}
                }
            }
        }
        if (items.isEmpty()) {
            try { items = filterRelevant(parse(fetch(ENDPOINT + encoded, true)), safeQuery); }
            catch (Throwable ignored) {}
        }
        if (items.isEmpty()) {
            try {
                String localeSuffix = Locale.getDefault().getLanguage().startsWith("zh")
                        ? "&setlang=zh-Hans&cc=CN" : "&setlang=en&cc=US";
                items = filterRelevant(parse(fetchWithAgent(
                        ENDPOINT + encoded + localeSuffix, FIREFOX_USER_AGENT)), safeQuery);
                if (items.isEmpty()) {
                    items = filterRelevant(parse(fetchWithAgent(
                            ENDPOINT + encoded + localeSuffix, MOBILE_USER_AGENT)), safeQuery);
                }
            } catch (Throwable ignored) {}
        }
        if (items.isEmpty()) {
            // Bing increasingly serves a Turnstile page to crawler-shaped HTML requests.
            // Its RSS endpoint is the same Bing index and remains suitable for compact Agent
            // citations, so use it only when the RikkaHub-compatible HTML path has no results.
            try {
                items = filterRelevant(parseRss(fetch(
                        "https://www.bing.com/search?format=rss&q=" + encoded, false)),
                        safeQuery);
            } catch (Throwable ignored) {}
        }
        if (items.isEmpty()) {
            return new SearchResult(false, safeQuery, items,
                    "Bing returned no parseable results");
        }
        return new SearchResult(true, safeQuery, enrichWithPageContent(items), "");
    }

    private static String fetch(String url, boolean desktopHeaders) throws Exception {
        return fetchWithAgent(url, desktopHeaders ? USER_AGENT : "Mozilla/5.0");
    }

    private static String fetchWithAgent(String url, String userAgent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language",
                Locale.getDefault().getLanguage().startsWith("zh")
                        ? "zh-CN,zh;q=0.9,en;q=0.7" : "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Referer", "https://www.bing.com/");
        connection.setRequestProperty("Cookie", "SRCHHPGUSR=ULSR=1");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + status);
        }
        InputStream input = connection.getInputStream();
        String encoding = connection.getContentEncoding();
        if (encoding != null && encoding.toLowerCase(Locale.US).contains("gzip")) {
            input = new GZIPInputStream(input);
        } else if (encoding != null
                && encoding.toLowerCase(Locale.US).contains("deflate")) {
            input = new InflaterInputStream(input);
        }
        byte[] bytes;
        try {
            bytes = readBounded(input, MAX_RESPONSE_BYTES);
        } finally {
            try { input.close(); } catch (Throwable ignored) {}
            connection.disconnect();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static List<Item> parse(String html) {
        ArrayList<Item> items = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        if (html == null || html.length() == 0) return items;
        Matcher blocks = RESULT_BLOCK.matcher(html);
        while (blocks.find() && items.size() < MAX_RESULTS) {
            String block = blocks.group(2);
            Matcher link = TITLE_LINK.matcher(block);
            String target;
            String title;
            if (link.find()) {
                target = normalizeBingTarget(decode(link.group(2)).trim());
                title = cleanText(link.group(3));
            } else {
                Matcher mobileLink = MOBILE_TITLE_LINK.matcher(block);
                if (!mobileLink.find()) continue;
                target = normalizeBingTarget(decode(mobileLink.group(3)).trim());
                title = cleanText(mobileLink.group(4));
            }
            if (!isHttpUrl(target) || title.length() == 0 || !seenUrls.add(target)) continue;
            String snippet = "";
            Matcher caption = CAPTION.matcher(block);
            if (caption.find()) {
                Matcher paragraph = PARAGRAPH.matcher(caption.group(2));
                snippet = cleanText(paragraph.find() ? paragraph.group(1) : caption.group(2));
            } else {
                Matcher paragraph = PARAGRAPH.matcher(block);
                if (paragraph.find()) snippet = cleanText(paragraph.group(1));
            }
            int index = items.size() + 1;
            items.add(new Item(shortId(target), index, title, target,
                    snippet, hostName(target)));
        }
        return items;
    }

    static List<Item> parseRss(String xml) {
        ArrayList<Item> items = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        if (xml == null || xml.length() == 0) return items;
        Matcher matcher = RSS_ITEM.matcher(xml);
        while (matcher.find() && items.size() < MAX_RESULTS) {
            String item = matcher.group(1);
            String title = elementText(RSS_TITLE, item);
            String target = normalizeBingTarget(elementText(RSS_LINK, item));
            String snippet = elementText(RSS_DESCRIPTION, item);
            if (title.length() == 0 || !isHttpUrl(target) || !seenUrls.add(target)) continue;
            int index = items.size() + 1;
            items.add(new Item(shortId(target), index, title, target,
                    snippet, hostName(target)));
        }
        return items;
    }

    private static List<Item> enrichWithPageContent(List<Item> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        final ArrayList<Item> originals = new ArrayList<>(source);
        final Item[] enriched = new Item[originals.size()];
        int count = Math.min(MAX_PAGES_TO_READ, originals.size());
        final CountDownLatch latch = new CountDownLatch(count);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, count));
        for (int index = 0; index < count; index++) {
            final int position = index;
            executor.execute(new Runnable() {
                @Override public void run() {
                    Item item = originals.get(position);
                    try {
                        String page = fetch(item.url, false);
                        String content = extractDocumentText(page);
                        if (content.length() >= 240) {
                            enriched[position] = new Item(item.id, item.index,
                                    item.title, item.url, item.snippet,
                                    item.siteName, content);
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        try { latch.await(12, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        executor.shutdownNow();
        ArrayList<Item> result = new ArrayList<>(originals.size());
        for (int index = 0; index < originals.size(); index++) {
            result.add(enriched[index] == null ? originals.get(index) : enriched[index]);
        }
        return result;
    }

    private static List<Item> filterRelevant(List<Item> source, String query) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        ArrayList<String> tokens = new ArrayList<>();
        String[] rawTokens = (query == null ? "" : query.toLowerCase(Locale.US))
                .split("[^a-z0-9]+");
        for (String token : rawTokens) {
            if (token.length() < 3 || isSearchStopWord(token) || tokens.contains(token)) continue;
            tokens.add(token);
        }
        ArrayList<String> cjkTerms = meaningfulCjkTerms(query);
        // A date such as "2026年8月23日" must not become a mandatory English token.
        // Chinese news titles commonly omit the year even when the RSS publication date is
        // current, which previously caused every valid result to be discarded locally.
        if (!cjkTerms.isEmpty()) {
            for (int index = tokens.size() - 1; index >= 0; index--) {
                if (tokens.get(index).matches("[0-9]+")) tokens.remove(index);
            }
        }
        ArrayList<ScoredItem> scored = new ArrayList<>();
        for (int rank = 0; rank < source.size(); rank++) {
            Item item = source.get(rank);
            String host = hostName(item.url).toLowerCase(Locale.US);
            if (host.equals("bing.com") || host.endsWith(".bing.com")) continue;
            String haystack = (item.title + " " + item.snippet + " " + host)
                    .toLowerCase(Locale.US);
            int matches = 0;
            for (String token : tokens) if (haystack.contains(token)) matches++;
            int cjkMatches = 0;
            for (String term : cjkTerms) if (haystack.contains(term)) cjkMatches++;
            int required = tokens.isEmpty() ? 0 : Math.max(1, (tokens.size() + 1) / 2);
            if (matches < required || (!cjkTerms.isEmpty() && cjkMatches == 0)) continue;
            scored.add(new ScoredItem(item,
                    matches * 100 + cjkMatches * 130
                            + authorityBonus(host) - rank));
        }
        Collections.sort(scored, new Comparator<ScoredItem>() {
            @Override public int compare(ScoredItem left, ScoredItem right) {
                return Integer.compare(right.score, left.score);
            }
        });
        ArrayList<Item> result = new ArrayList<>();
        java.util.HashMap<String, Integer> perHost = new java.util.HashMap<>();
        for (ScoredItem scoredItem : scored) {
            String host = hostName(scoredItem.item.url).toLowerCase(Locale.US);
            Integer count = perHost.get(host);
            if (count != null && count.intValue() >= 2) continue;
            perHost.put(host, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            Item item = scoredItem.item;
            result.add(new Item(item.id, result.size() + 1, item.title, item.url,
                    item.snippet, item.siteName, item.content));
            if (result.size() >= MAX_RESULTS) break;
        }
        return result;
    }

    private static boolean isSearchStopWord(String token) {
        return "the".equals(token) || "and".equals(token) || "for".equals(token)
                || "with".equals(token) || "from".equals(token) || "how".equals(token)
                || "what".equals(token) || "official".equals(token);
    }

    private static ArrayList<String> meaningfulCjkTerms(String query) {
        ArrayList<String> terms = new ArrayList<>();
        String[] chunks = (query == null ? "" : query.toLowerCase(Locale.US))
                .split("[\\s\\p{Punct}，。！？、；：]+?");
        for (String chunk : chunks) {
            String cleaned = chunk.replaceAll("[0-9年月日号时分秒]", "").trim();
            int han = 0;
            for (int index = 0; index < cleaned.length(); index++) {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(cleaned.charAt(index));
                if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                        || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) han++;
            }
            if (han >= 2 && !terms.contains(cleaned)) terms.add(cleaned);
        }
        return terms;
    }

    private static boolean isNewsQuery(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.US);
        return lower.contains("新闻") || lower.contains("要闻")
                || lower.contains("最新消息") || lower.contains("今日")
                || lower.contains(" news") || lower.startsWith("news")
                || lower.contains("latest") || lower.contains("breaking")
                || lower.contains("today");
    }

    private static String relaxNewsQuery(String query) {
        String relaxed = query == null ? "" : query;
        relaxed = relaxed.replaceAll("(?i)\\b20[0-9]{2}[-/.年][0-9]{1,2}[-/.月][0-9]{1,2}日?\\b", " ");
        relaxed = relaxed.replaceAll("(?i)\\b20[0-9]{2}年?\\b", " ");
        relaxed = relaxed.replaceAll("(?i)\\b(today|latest|breaking)\\b", " ");
        relaxed = relaxed.replace("今日", " ").replace("今天", " ");
        relaxed = relaxed.replaceAll("\\s+", " ").trim();
        return relaxed.length() == 0 ? query.trim() : relaxed;
    }

    private static int authorityBonus(String host) {
        if (host == null) return 0;
        if (host.startsWith("developer.") || host.startsWith("developers.")
                || host.startsWith("docs.") || host.startsWith("support.")) return 260;
        if (host.endsWith(".gov") || host.contains(".gov.")
                || host.endsWith(".edu") || host.contains(".edu.")) return 230;
        if ("github.com".equals(host) || host.endsWith(".wikipedia.org")) return 160;
        return 0;
    }

    private static final class ScoredItem {
        final Item item;
        final int score;
        ScoredItem(Item item, int score) {
            this.item = item;
            this.score = score;
        }
    }

    static String extractDocumentText(String html) {
        if (html == null || html.length() == 0) return "";
        String lower = html.substring(0, Math.min(html.length(), 4096))
                .toLowerCase(Locale.US);
        if (!lower.contains("<html") && !lower.contains("<body")
                && !lower.contains("<article") && !lower.contains("<main")) return "";
        String clean = DOCUMENT_NOISE.matcher(html).replaceAll(" ");
        Matcher article = ARTICLE.matcher(clean);
        String body;
        if (article.find()) body = article.group(1);
        else {
            Matcher main = MAIN.matcher(clean);
            if (main.find()) body = main.group(1);
            else {
                Matcher pageBody = BODY.matcher(clean);
                body = pageBody.find() ? pageBody.group(1) : clean;
            }
        }
        String text = TAG.matcher(body).replaceAll(" ");
        text = decode(text).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return text.length() <= MAX_PAGE_TEXT
                ? text : text.substring(0, MAX_PAGE_TEXT) + "…";
    }

    private static String elementText(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source == null ? "" : source);
        return matcher.find() ? cleanText(matcher.group(1)) : "";
    }

    private static byte[] readBounded(InputStream input, int maximum) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
        byte[] buffer = new byte[8192];
        int total = 0;
        while (true) {
            int count = input.read(buffer);
            if (count < 0) break;
            total += count;
            if (total > maximum) throw new IllegalStateException("response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String cleanText(String html) {
        String value = TAG.matcher(html == null ? "" : html).replaceAll(" ");
        value = decode(value).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return value.length() <= 1200 ? value : value.substring(0, 1200) + "…";
    }

    private static String decode(String value) {
        Matcher matcher = ENTITY.matcher(value == null ? "" : value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(decodeEntity(matcher.group(1))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String decodeEntity(String entity) {
        if (entity == null) return "";
        try {
            if (entity.startsWith("#x")) {
                return new String(Character.toChars(Integer.parseInt(entity.substring(2), 16)));
            }
            if (entity.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(entity.substring(1))));
            }
        } catch (Throwable ignored) {}
        if ("amp".equals(entity)) return "&";
        if ("lt".equals(entity)) return "<";
        if ("gt".equals(entity)) return ">";
        if ("quot".equals(entity)) return "\"";
        if ("apos".equals(entity) || "#39".equals(entity)) return "'";
        if ("nbsp".equals(entity)) return " ";
        return "&" + entity + ";";
    }

    /** Resolves Bing's /ck/a redirect into the publisher URL used for ranking and page reading. */
    static String normalizeBingTarget(String value) {
        if (!isHttpUrl(value)) return value == null ? "" : value;
        String host = hostName(value).toLowerCase(Locale.US);
        if (!(host.equals("bing.com") || host.endsWith(".bing.com"))) return value;
        try {
            String query = new URI(value).getRawQuery();
            if (query == null) return value;
            for (String part : query.split("&")) {
                int equals = part.indexOf('=');
                if (equals <= 0 || !"u".equals(part.substring(0, equals))) continue;
                String encoded = URLDecoder.decode(part.substring(equals + 1), "UTF-8");
                if (encoded.startsWith("a1")) encoded = encoded.substring(2);
                String decoded = decodeBase64Url(encoded);
                if (isHttpUrl(decoded)) return decoded;
            }
        } catch (Throwable ignored) {}
        return value;
    }

    private static String decodeBase64Url(String value) {
        if (value == null || value.length() == 0) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream(value.length());
        int accumulator = 0;
        int bits = 0;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '=') break;
            int digit;
            if (c >= 'A' && c <= 'Z') digit = c - 'A';
            else if (c >= 'a' && c <= 'z') digit = c - 'a' + 26;
            else if (c >= '0' && c <= '9') digit = c - '0' + 52;
            else if (c == '+' || c == '-') digit = 62;
            else if (c == '/' || c == '_') digit = 63;
            else continue;
            accumulator = (accumulator << 6) | digit;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output.write((accumulator >> bits) & 0xff);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private static boolean isHttpUrl(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String hostName(String value) {
        try {
            String host = new URI(value).getHost();
            if (host == null) return "";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String shortId(String value) {
        long hash = value == null ? 0L : value.hashCode() & 0xffffffffL;
        return String.format(Locale.US, "%06x", hash).substring(0, 6);
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        if (message == null || message.trim().length() == 0) {
            message = error.getClass().getSimpleName();
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }
}
