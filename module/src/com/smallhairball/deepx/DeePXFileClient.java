package com.smallhairball.deepx;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client helper for accessing the host's private sandbox via DeePXFileProvider.
 * Provides transparent file reading, writing, directory listing, and metadata queries
 * for the DeePX module.
 */
public final class DeePXFileClient {
    private static final String TAG = "DeePX-FileClient";
    public static final String DEFAULT_HOST_PACKAGE = "com.deepseek.chat";

    public static class FileItem {
        public final String name;
        public final long size;
        public final long lastModified;
        public final String mimeType;
        public final boolean isDirectory;
        public final boolean canRead;
        public final boolean canWrite;
        public final String path;
        public final Uri uri;

        public FileItem(String name, long size, long lastModified, String mimeType,
                        boolean isDirectory, boolean canRead, boolean canWrite,
                        String path, Uri uri) {
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
            this.mimeType = mimeType;
            this.isDirectory = isDirectory;
            this.canRead = canRead;
            this.canWrite = canWrite;
            this.path = path;
            this.uri = uri;
        }
    }

    private DeePXFileClient() {}

    public static Uri getUri(String hostPackage, String rootTag, String relativePath) {
        String pkg = (hostPackage != null && !hostPackage.isEmpty()) ? hostPackage : DEFAULT_HOST_PACKAGE;
        String authority = pkg + ".deekseep.provider";
        String path = (rootTag != null && !rootTag.isEmpty() ? rootTag + "/" : "")
                + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
        return new Uri.Builder()
                .scheme("content")
                .authority(authority)
                .encodedPath(path)
                .build();
    }

    public static Uri getFilesUri(String relativePath) {
        return getUri(DEFAULT_HOST_PACKAGE, "files", relativePath);
    }

    public static Uri getDatabasesUri(String relativePath) {
        return getUri(DEFAULT_HOST_PACKAGE, "databases", relativePath);
    }

    public static Uri getSharedPrefsUri(String relativePath) {
        return getUri(DEFAULT_HOST_PACKAGE, "shared_prefs", relativePath);
    }

    public static Uri getDataUri(String relativePath) {
        return getUri(DEFAULT_HOST_PACKAGE, "data", relativePath);
    }

    public static InputStream openInputStream(Context context, Uri uri) throws IOException {
        ContentResolver cr = context.getContentResolver();
        InputStream is = cr.openInputStream(uri);
        if (is == null) throw new IOException("Failed to open InputStream for URI: " + uri);
        return is;
    }

    public static OutputStream openOutputStream(Context context, Uri uri, boolean append) throws IOException {
        ContentResolver cr = context.getContentResolver();
        OutputStream os = cr.openOutputStream(uri, append ? "wa" : "wt");
        if (os == null) throw new IOException("Failed to open OutputStream for URI: " + uri);
        return os;
    }

    public static byte[] readFile(Context context, Uri uri) throws IOException {
        try (InputStream is = openInputStream(context, uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    public static String readString(Context context, Uri uri) throws IOException {
        return new String(readFile(context, uri), StandardCharsets.UTF_8);
    }

    public static void writeFile(Context context, Uri uri, byte[] data) throws IOException {
        try (OutputStream os = openOutputStream(context, uri, false)) {
            os.write(data);
            os.flush();
        }
    }

    public static void writeString(Context context, Uri uri, String text) throws IOException {
        writeFile(context, uri, text.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean exists(Context context, Uri uri) {
        try {
            ContentResolver cr = context.getContentResolver();
            Bundle reply = cr.call(uri, "file_exists", uri.toString(), null);
            if (reply != null && reply.containsKey("exists")) {
                return reply.getBoolean("exists");
            }
            try (Cursor cursor = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                return cursor != null && cursor.getCount() > 0;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isDirectory(Context context, Uri uri) {
        try {
            ContentResolver cr = context.getContentResolver();
            Bundle reply = cr.call(uri, "is_directory", uri.toString(), null);
            if (reply != null && reply.containsKey("is_directory")) {
                return reply.getBoolean("is_directory");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean delete(Context context, Uri uri, boolean recursive) {
        try {
            ContentResolver cr = context.getContentResolver();
            if (recursive) {
                Bundle reply = cr.call(uri, "delete_file", uri.toString(), null);
                if (reply != null && reply.containsKey("success")) {
                    return reply.getBoolean("success");
                }
            }
            return cr.delete(uri, recursive ? "recursive" : null, null) > 0;
        } catch (Throwable t) {
            Log.w(TAG, "Delete failed: " + uri, t);
            return false;
        }
    }

    public static boolean mkdirs(Context context, Uri uri) {
        try {
            ContentResolver cr = context.getContentResolver();
            Bundle reply = cr.call(uri, "mkdirs", uri.toString(), null);
            if (reply != null && reply.containsKey("success")) {
                return reply.getBoolean("success");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static List<String> listFileNames(Context context, Uri uri) {
        try {
            ContentResolver cr = context.getContentResolver();
            Bundle reply = cr.call(uri, "list_files", uri.toString(), null);
            if (reply != null && reply.containsKey("files")) {
                ArrayList<String> files = reply.getStringArrayList("files");
                if (files != null) return files;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    public static List<FileItem> listFiles(Context context, Uri uri) {
        List<FileItem> items = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null) return items;
            int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
            int modIdx = cursor.getColumnIndex("last_modified");
            int mimeIdx = cursor.getColumnIndex("mime_type");
            int isDirIdx = cursor.getColumnIndex("is_directory");
            int canReadIdx = cursor.getColumnIndex("can_read");
            int canWriteIdx = cursor.getColumnIndex("can_write");
            int pathIdx = cursor.getColumnIndex("path");
            int uriIdx = cursor.getColumnIndex("uri");

            while (cursor.moveToNext()) {
                String name = nameIdx >= 0 ? cursor.getString(nameIdx) : "";
                long size = sizeIdx >= 0 ? cursor.getLong(sizeIdx) : 0;
                long mod = modIdx >= 0 ? cursor.getLong(modIdx) : 0;
                String mime = mimeIdx >= 0 ? cursor.getString(mimeIdx) : "";
                boolean isDir = isDirIdx >= 0 && cursor.getInt(isDirIdx) == 1;
                boolean canRead = canReadIdx < 0 || cursor.getInt(canReadIdx) == 1;
                boolean canWrite = canWriteIdx < 0 || cursor.getInt(canWriteIdx) == 1;
                String path = pathIdx >= 0 ? cursor.getString(pathIdx) : "";
                String uStr = uriIdx >= 0 ? cursor.getString(uriIdx) : "";
                Uri itemUri = (uStr != null && !uStr.isEmpty()) ? Uri.parse(uStr) : uri;

                items.add(new FileItem(name, size, mod, mime, isDir, canRead, canWrite, path, itemUri));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to list files for " + uri, t);
        }
        return items;
    }
}
