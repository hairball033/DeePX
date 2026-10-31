package com.smallhairball.deepx;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Minimal read-only AXML parser for the manifest package versionName attribute. */
final class AndroidManifestVersion {
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int UTF8_FLAG = 0x00000100;
    private static final int TYPE_STRING = 0x03;

    private AndroidManifestVersion() {}

    static String readFromApk(String apkPath) {
        if (apkPath == null) return null;
        ZipFile apk = null;
        try {
            apk = new ZipFile(new File(apkPath));
            ZipEntry manifest = apk.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.getSize() > 2 * 1024 * 1024L) return null;
            InputStream input = apk.getInputStream(manifest);
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    manifest.getSize() > 0 ? (int) manifest.getSize() : 32 * 1024);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (output.size() + read > 2 * 1024 * 1024) return null;
                    output.write(buffer, 0, read);
                }
            } finally { input.close(); }
            return parse(output.toByteArray());
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (apk != null) try { apk.close(); } catch (Throwable ignored) {}
        }
    }

    static String parse(byte[] xml) {
        if (xml == null || xml.length < 8) return null;
        String[] strings = null;
        int offset = u16(xml, 2);
        if (offset < 8) offset = 8;
        while (offset + 8 <= xml.length) {
            int type = u16(xml, offset);
            int headerSize = u16(xml, offset + 2);
            int chunkSize = i32(xml, offset + 4);
            if (headerSize < 8 || chunkSize < headerSize || offset + chunkSize > xml.length) {
                return null;
            }
            if (type == RES_STRING_POOL_TYPE) {
                strings = stringPool(xml, offset, headerSize, chunkSize);
            } else if (type == RES_XML_START_ELEMENT_TYPE && strings != null
                    && headerSize >= 16 && offset + headerSize + 20 <= xml.length) {
                int extension = offset + headerSize;
                String element = stringAt(strings, i32(xml, extension + 4));
                if ("manifest".equals(element)) {
                    int attributeStart = u16(xml, extension + 8);
                    int attributeSize = u16(xml, extension + 10);
                    int attributeCount = u16(xml, extension + 12);
                    int attributes = extension + attributeStart;
                    if (attributeSize < 20 || attributes < extension
                            || attributes + (long) attributeSize * attributeCount
                            > offset + chunkSize) return null;
                    for (int i = 0; i < attributeCount; i++) {
                        int attribute = attributes + i * attributeSize;
                        String name = stringAt(strings, i32(xml, attribute + 4));
                        if (!"versionName".equals(name)) continue;
                        int rawValue = i32(xml, attribute + 8);
                        if (rawValue >= 0) return stringAt(strings, rawValue);
                        int valueType = xml[attribute + 15] & 0xff;
                        if (valueType == TYPE_STRING) {
                            return stringAt(strings, i32(xml, attribute + 16));
                        }
                        return null;
                    }
                }
            }
            offset += chunkSize;
        }
        return null;
    }

    private static String[] stringPool(byte[] data, int chunk, int headerSize, int chunkSize) {
        if (headerSize < 28 || chunk + headerSize > data.length) return null;
        int count = i32(data, chunk + 8);
        int flags = i32(data, chunk + 16);
        int start = i32(data, chunk + 20);
        if (count < 0 || count > 200_000 || start < headerSize
                || chunk + headerSize + (long) count * 4 > chunk + chunkSize) return null;
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            int relative = i32(data, chunk + headerSize + i * 4);
            int at = chunk + start + relative;
            if (at < chunk || at >= chunk + chunkSize) return null;
            out[i] = (flags & UTF8_FLAG) != 0
                    ? decodeUtf8(data, at, chunk + chunkSize)
                    : decodeUtf16(data, at, chunk + chunkSize);
        }
        return out;
    }

    private static String decodeUtf8(byte[] data, int at, int end) {
        int[] first = length8(data, at, end);
        if (first == null) return null;
        int[] second = length8(data, first[1], end);
        if (second == null || second[0] < 0 || second[1] + second[0] > end) return null;
        return new String(data, second[1], second[0], StandardCharsets.UTF_8);
    }

    private static int[] length8(byte[] data, int at, int end) {
        if (at >= end) return null;
        int value = data[at++] & 0xff;
        if ((value & 0x80) != 0) {
            if (at >= end) return null;
            value = ((value & 0x7f) << 8) | (data[at++] & 0xff);
        }
        return new int[]{value, at};
    }

    private static String decodeUtf16(byte[] data, int at, int end) {
        if (at + 2 > end) return null;
        int length = u16(data, at);
        at += 2;
        if ((length & 0x8000) != 0) {
            if (at + 2 > end) return null;
            length = ((length & 0x7fff) << 16) | u16(data, at);
            at += 2;
        }
        int bytes = length * 2;
        if (length < 0 || at + bytes > end) return null;
        return new String(data, at, bytes, StandardCharsets.UTF_16LE);
    }

    private static String stringAt(String[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : null;
    }

    private static int u16(byte[] data, int at) {
        if (at < 0 || at + 2 > data.length) return -1;
        return (data[at] & 0xff) | ((data[at + 1] & 0xff) << 8);
    }

    private static int i32(byte[] data, int at) {
        if (at < 0 || at + 4 > data.length) return -1;
        return (data[at] & 0xff) | ((data[at + 1] & 0xff) << 8)
                | ((data[at + 2] & 0xff) << 16) | ((data[at + 3] & 0xff) << 24);
    }
}
