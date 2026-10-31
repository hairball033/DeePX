package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Parses bounded inline image parts accepted by OpenAI and Anthropic clients. */
public final class z4 {
    static final int MAX_IMAGES = 8;
    static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 24 * 1024 * 1024;

    public static final class Attachment {
        public final byte[] bytes;
        public final String mimeType;
        public final String fileName;

        public Attachment(byte[] bytes, String mimeType, String fileName) {
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }

    private z4() {}

    public static List<Attachment> parse(JSONObject request) throws z2.GatewayException {
        if (request == null) return Collections.emptyList();
        ArrayList<Attachment> out = new ArrayList<Attachment>();
        int[] total = {0};
        scan(request, 0, out, total);
        return out.isEmpty() ? Collections.<Attachment>emptyList()
                : Collections.unmodifiableList(out);
    }

    public static boolean isImagePart(JSONObject object) {
        if (object == null) return false;
        String type = object.optString("type", "").trim().toLowerCase(Locale.US);
        if ("image_url".equals(type) || "input_image".equals(type)
                || "image".equals(type)) return true;
        if (!"input_file".equals(type)) return false;
        String mime = firstNonBlank(object.optString("mime_type", ""),
                object.optString("media_type", ""));
        return mime.toLowerCase(Locale.US).startsWith("image/");
    }

    private static void scan(Object value, int depth, List<Attachment> out, int[] total)
            throws z2.GatewayException {
        if (value == null || value == JSONObject.NULL || depth > 16) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) scan(array.opt(i), depth + 1, out, total);
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        if (isImagePart(object)) {
            addPart(object, out, total);
            return;
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) scan(object.opt(keys.next()), depth + 1, out, total);
    }

    private static void addPart(JSONObject part, List<Attachment> out, int[] total)
            throws z2.GatewayException {
        if (out.size() >= MAX_IMAGES) {
            throw new z2.GatewayException(413, "too_many_images",
                    "At most " + MAX_IMAGES + " images are accepted per request");
        }
        String type = part.optString("type", "").toLowerCase(Locale.US);
        String encoded = "";
        String declaredMime = firstNonBlank(part.optString("mime_type", ""),
                part.optString("media_type", ""));
        if ("image".equals(type)) {
            JSONObject source = part.optJSONObject("source");
            if (source != null) {
                String sourceType = source.optString("type", "base64");
                if (!"base64".equalsIgnoreCase(sourceType)) {
                    throw unsupportedRemote();
                }
                encoded = source.optString("data", "");
                declaredMime = firstNonBlank(source.optString("media_type", ""), declaredMime);
            }
        } else {
            Object raw = part.opt("image_url");
            if (raw instanceof JSONObject) raw = ((JSONObject) raw).opt("url");
            if (raw == null || raw == JSONObject.NULL) raw = part.opt("file_data");
            if (raw == null || raw == JSONObject.NULL) raw = part.opt("image_data");
            if (raw != null && raw != JSONObject.NULL) encoded = String.valueOf(raw).trim();
        }
        String mime = declaredMime;
        if (encoded.startsWith("data:")) {
            int comma = encoded.indexOf(',');
            if (comma <= 5) throw invalidImage("Malformed image data URL");
            String header = encoded.substring(5, comma);
            int semicolon = header.indexOf(';');
            mime = semicolon >= 0 ? header.substring(0, semicolon) : header;
            if (!header.toLowerCase(Locale.US).contains(";base64")) {
                throw invalidImage("Image data URLs must use base64 encoding");
            }
            encoded = encoded.substring(comma + 1);
        } else if (encoded.startsWith("http://") || encoded.startsWith("https://")) {
            throw unsupportedRemote();
        }
        mime = mime == null ? "" : mime.trim().toLowerCase(Locale.US);
        if (!mime.startsWith("image/")) {
            throw invalidImage("Image MIME type is required (for example image/png)");
        }
        if (encoded.length() == 0) throw invalidImage("Image base64 data is empty");
        byte[] decoded;
        try {
            decoded = decodeBase64(encoded);
        } catch (Throwable error) {
            throw invalidImage("Image base64 data is invalid");
        }
        if (decoded.length == 0 || decoded.length > MAX_IMAGE_BYTES) {
            throw new z2.GatewayException(413, "image_too_large",
                    "Each image must be between 1 byte and 10 MiB");
        }
        if (total[0] + decoded.length > MAX_TOTAL_BYTES) {
            throw new z2.GatewayException(413, "images_too_large",
                    "Combined image data must not exceed 24 MiB");
        }
        total[0] += decoded.length;
        String suppliedName = firstNonBlank(part.optString("filename", ""),
                part.optString("name", ""));
        String fileName = safeFileName(suppliedName, mime, out.size() + 1);
        out.add(new Attachment(decoded, mime, fileName));
    }

    /** Strict decoder kept independent of android.util.Base64 so protocol tests run on the JVM. */
    private static byte[] decodeBase64(String value) throws Exception {
        if (value == null || value.length() == 0
                || value.length() > (MAX_IMAGE_BYTES * 4 / 3) + 32_768) {
            throw new IllegalArgumentException("invalid base64 length");
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(
                Math.min(MAX_IMAGE_BYTES, value.length() * 3 / 4));
        int buffer = 0;
        int bits = 0;
        int symbols = 0;
        int padding = 0;
        boolean ended = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') continue;
            if (ch == '=') {
                ended = true;
                padding++;
                if (padding > 2) throw new IllegalArgumentException("too much padding");
                continue;
            }
            if (ended) throw new IllegalArgumentException("data after padding");
            int digit;
            if (ch >= 'A' && ch <= 'Z') digit = ch - 'A';
            else if (ch >= 'a' && ch <= 'z') digit = ch - 'a' + 26;
            else if (ch >= '0' && ch <= '9') digit = ch - '0' + 52;
            else if (ch == '+') digit = 62;
            else if (ch == '/') digit = 63;
            else throw new IllegalArgumentException("invalid base64 character");
            symbols++;
            buffer = (buffer << 6) | digit;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output.write((buffer >> bits) & 0xff);
                buffer &= bits == 0 ? 0 : (1 << bits) - 1;
                if (output.size() > MAX_IMAGE_BYTES) {
                    throw new IllegalArgumentException("decoded data too large");
                }
            }
        }
        if (symbols % 4 == 1 || (padding > 0 && (symbols + padding) % 4 != 0)
                || (bits > 0 && buffer != 0)) {
            throw new IllegalArgumentException("invalid base64 tail");
        }
        return output.toByteArray();
    }

    private static z2.GatewayException unsupportedRemote() {
        return new z2.GatewayException(400, "remote_image_not_supported",
                "Use a base64 data URL (OpenAI) or base64 image source (Anthropic); remote image URLs are not fetched");
    }

    private static z2.GatewayException invalidImage(String message) {
        return new z2.GatewayException(400, "invalid_image", message);
    }

    private static String safeFileName(String raw, String mime, int index) {
        String extension = extension(mime);
        String value = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.length() == 0) value = "image_" + index + extension;
        if (value.length() > 80) value = value.substring(0, 80);
        if (value.lastIndexOf('.') < 0) value += extension;
        return value;
    }

    private static String extension(String mime) {
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        if ("image/bmp".equals(mime)) return ".bmp";
        return ".jpg";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && first.trim().length() > 0) return first.trim();
        return second == null ? "" : second.trim();
    }
}
