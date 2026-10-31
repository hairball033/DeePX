package com.smallhairball.deepx;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded GitHub Releases check per DeepSeek process start. */
final class ModuleUpdateChecker {
    private static final String LATEST =
            "https://api.github.com/repos/hairball033/DeePX/releases/latest";
    private static final String IGNORED = "deekseep_ignored_release";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private ModuleUpdateChecker() {}

    static void checkOnStartup(final Activity activity) {
        if (activity == null || activity.isFinishing()
                || !STARTED.compareAndSet(false, true)) return;
        final Context app = activity.getApplicationContext() == null
                ? activity : activity.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(LATEST).openConnection();
                    connection.setConnectTimeout(4_000);
                    connection.setReadTimeout(6_000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("Accept",
                            "application/vnd.github+json");
                    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                    connection.setRequestProperty("User-Agent",
                            "DeePX/" + BuildInfo.MODULE_VERSION);
                    int code = connection.getResponseCode();
                    if (code != 200) return;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            connection.getInputStream(), "UTF-8"));
                    StringBuilder body = new StringBuilder();
                    char[] buffer = new char[4096];
                    int count;
                    while ((count = reader.read(buffer)) >= 0 && body.length() <= 512 * 1024) {
                        if (count > 0) body.append(buffer, 0, count);
                    }
                    reader.close();
                    if (body.length() > 512 * 1024) return;
                    JSONObject release = new JSONObject(body.toString());
                    if (release.optBoolean("draft", false)
                            || release.optBoolean("prerelease", false)) return;
                    final String tag = clean(release.optString("tag_name", ""));
                    if (tag.length() == 0 || compare(tag, BuildInfo.MODULE_VERSION) <= 0
                            || tag.equals(read(new File(app.getFilesDir(), IGNORED)))) return;
                    Asset asset = chooseAsset(release.optJSONArray("assets"));
                    if (asset == null) return;
                    final Asset selected = asset;
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (activity.isFinishing()
                                    || (Build.VERSION.SDK_INT >= 17
                                    && activity.isDestroyed())) return;
                            show(activity, tag, selected);
                        }
                    });
                } catch (Throwable ignored) {
                    // Startup checking is advisory. Network, JSON or repository failures never
                    // affect DeepSeek startup and are intentionally not retried in this process.
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }, "DeePX-Update-Check").start();
    }

    private static void show(final Activity activity, final String tag, final Asset asset) {
        new AlertDialog.Builder(activity)
                .setTitle(UiLanguage.text(activity,
                        "发现 DeePX 新版本", "DeePX update available"))
                .setMessage(UiLanguage.text(activity, "最新版本：", "Latest version: ")
                        + tag + "\n" + UiLanguage.text(activity,
                        "下载后请在模块管理器中更新并重启 DeepSeek。",
                        "After downloading, update it in the module manager and restart DeepSeek."))
                .setPositiveButton(UiLanguage.text(activity, "下载", "Download"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface ignored, int which) {
                                download(activity, asset);
                            }
                        })
                .setNegativeButton(UiLanguage.text(activity, "忽略此版本", "Ignore this version"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface ignored, int which) {
                                write(new File(activity.getFilesDir(), IGNORED), tag);
                            }
                        })
                .setNeutralButton(UiLanguage.text(activity, "以后提醒", "Later"), null)
                .show();
    }

    private static void download(Activity activity, Asset asset) {
        try {
            DownloadManager manager = (DownloadManager)
                    activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager unavailable");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(asset.url));
            request.setTitle(asset.name);
            request.setDescription(UiLanguage.text(activity,
                    "正在下载 DeePX 更新", "Downloading DeePX update"));
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setMimeType("application/vnd.android.package-archive");
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    asset.name);
            manager.enqueue(request);
        } catch (Throwable error) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(asset.url)));
            } catch (Throwable ignored) {}
        }
    }

    private static Asset chooseAsset(JSONArray assets) {
        if (assets == null) return null;
        Asset fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "");
            String url = item.optString("browser_download_url", "");
            String lower = name.toLowerCase(Locale.US);
            if (!lower.endsWith(".apk") || !url.startsWith("https://github.com/")) continue;
            Asset value = new Asset(name, url);
            if (fallback == null) fallback = value;
            boolean closed = lower.contains("closed") || lower.contains("close")
                    || lower.contains("protected");
            boolean open = lower.contains("open");
            if (BuildInfo.PROTECTED_BUILD ? closed : open) return value;
        }
        return fallback;
    }

    static int compare(String left, String right) {
        int[] a = version(left);
        int[] b = version(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int[] version(String value) {
        String input = clean(value);
        java.util.ArrayList<Integer> parts = new java.util.ArrayList<Integer>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(input);
        while (matcher.find() && parts.size() < 4) {
            try { parts.add(Integer.parseInt(matcher.group())); }
            catch (Throwable ignored) { parts.add(0); }
        }
        // A maintenance build such as 1.7.4-fix must sort after the original
        // 1.7.4 release even though its three numeric components are identical.
        if (input.toLowerCase(Locale.US).contains("fix") && parts.size() < 4) {
            parts.add(1);
        }
        int[] result = new int[parts.size()];
        for (int i = 0; i < result.length; i++) result[i] = parts.get(i);
        return result;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String read(File file) {
        try {
            if (!file.isFile() || file.length() > 256L) return "";
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String value = reader.readLine();
            reader.close();
            return clean(value);
        } catch (Throwable ignored) { return ""; }
    }

    private static void write(File file, String value) {
        try {
            FileWriter writer = new FileWriter(file, false);
            writer.write(clean(value));
            writer.close();
        } catch (Throwable ignored) {}
    }

    private static final class Asset {
        final String name;
        final String url;

        Asset(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
