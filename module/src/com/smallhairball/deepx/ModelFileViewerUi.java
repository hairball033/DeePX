package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded local viewer/saver for model-created artifacts. */
final class ModelFileViewerUi {
    private static final int BRAND = 0xFF315EFB;

    private ModelFileViewerUi() {}

    static void show(Activity activity, ModelFileOutput.FileItem item) {
        if (activity == null || item == null || activity.isFinishing()) return;
        if (item.html()) showHtml(activity, item);
        else showText(activity, item);
    }

    private static void showText(final Activity activity, final ModelFileOutput.FileItem item) {
        final Dialog dialog = new Dialog(activity);
        boolean dark = DeePXUi.isDark(activity);
        int card = dark ? 0xFF23252A : 0xFFFFFFFF;
        int text = dark ? 0xFFF2F3F5 : 0xFF15181D;

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(card);
        background.setCornerRadius(dp(activity, 8));
        panel.setBackground(background);
        panel.addView(toolbar(activity, dialog, item, text));

        ScrollView scroll = new ScrollView(activity);
        TextView code = codeView(activity, item.content, text, dark);
        scroll.addView(code, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(activity, 10);
        panel.addView(scroll, scrollLp);
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels
                            - dp(activity, 24), dp(activity, 720)),
                    Math.min(activity.getResources().getDisplayMetrics().heightPixels
                            - dp(activity, 80), dp(activity, 760)));
        }
    }

    private static void showHtml(final Activity activity, final ModelFileOutput.FileItem item) {
        final Dialog dialog = new Dialog(activity,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final boolean dark = DeePXUi.isDark(activity);
        final int surface = dark ? 0xFF181A1F : 0xFFF5F7FA;
        final int text = dark ? 0xFFF2F3F5 : 0xFF15181D;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(surface);

        LinearLayout bar = toolbar(activity, dialog, item, text);
        final TextView codeTab = tab(activity, "代码", true, dark);
        final TextView renderTab = tab(activity, "渲染", false, dark);
        bar.addView(codeTab, new LinearLayout.LayoutParams(
                dp(activity, 68), dp(activity, 40)));
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                dp(activity, 68), dp(activity, 40));
        tabLp.leftMargin = dp(activity, 6);
        bar.addView(renderTab, tabLp);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56)));

        final FrameLayout body = new FrameLayout(activity);
        final ScrollView codeScroll = new ScrollView(activity);
        codeScroll.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 12));
        codeScroll.addView(codeView(activity, item.content, text, dark));
        final WebView preview = safeWebView(activity, item.content, dark);
        preview.setVisibility(View.GONE);
        body.addView(codeScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        codeTab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                codeScroll.setVisibility(View.VISIBLE);
                preview.setVisibility(View.GONE);
                styleTab(codeTab, true, dark);
                styleTab(renderTab, false, dark);
            }
        });
        renderTab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                codeScroll.setVisibility(View.GONE);
                preview.setVisibility(View.VISIBLE);
                styleTab(codeTab, false, dark);
                styleTab(renderTab, true, dark);
            }
        });
        dialog.setOnDismissListener(d -> {
            preview.stopLoading();
            preview.loadUrl("about:blank");
            preview.destroy();
        });
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout toolbar(final Activity activity, final Dialog dialog,
                                        final ModelFileOutput.FileItem item, int textColor) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = action(activity, "‹", textColor);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        TextView title = action(activity, item.name, textColor);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        TextView save = action(activity, "保存", BRAND);
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        save.setOnClickListener(v -> save(activity, item));
        bar.addView(save, new LinearLayout.LayoutParams(dp(activity, 60), dp(activity, 44)));
        return bar;
    }

    private static TextView action(Context context, String value, int color) {
        TextView view = new TextView(context);
        view.setText(UiLanguage.dynamic(context, value));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private static TextView codeView(Context context, String value, int color, boolean dark) {
        TextView code = new TextView(context);
        code.setText(value);
        code.setTextColor(color);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(true);
        code.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF202329 : 0xFFFFFFFF);
        bg.setCornerRadius(dp(context, 8));
        code.setBackground(bg);
        return code;
    }

    private static TextView tab(Context context, String title, boolean selected, boolean dark) {
        TextView tab = action(context, title, selected ? BRAND : (dark ? 0xFF999DA5 : 0xFF737983));
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tab.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        styleTab(tab, selected, dark);
        return tab;
    }

    private static void styleTab(TextView tab, boolean selected, boolean dark) {
        tab.setTextColor(selected ? BRAND : (dark ? 0xFF999DA5 : 0xFF737983));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? (dark ? 0xFF26345E : 0xFFE8EEFF)
                : android.graphics.Color.TRANSPARENT);
        bg.setCornerRadius(dp(tab.getContext(), 8));
        tab.setBackground(bg);
    }

    private static WebView safeWebView(Context context, String html, boolean dark) {
        WebView web = new WebView(context);
        web.setBackgroundColor(dark ? 0xFF181A1F : 0xFFFFFFFF);
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= 16) {
            web.getSettings().setAllowFileAccessFromFileURLs(false);
            web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        }
        web.getSettings().setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                String scheme = uri == null ? "" : uri.getScheme();
                if (scheme != null && !"about".equals(scheme) && !"data".equals(scheme)) {
                    return blocked();
                }
                return super.shouldInterceptRequest(view, request);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url != null && !url.startsWith("about:") && !url.startsWith("data:")) {
                    return blocked();
                }
                return super.shouldInterceptRequest(view, url);
            }
            private WebResourceResponse blocked() {
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }
        });
        String policy = "<meta http-equiv=\"Content-Security-Policy\" content=\""
                + "default-src 'none'; style-src 'unsafe-inline'; img-src data:; "
                + "font-src data:; media-src 'none'; connect-src 'none'; frame-src 'none'\">";
        String document = html == null ? "" : html;
        int head = document.toLowerCase(java.util.Locale.US).indexOf("<head>");
        document = head >= 0 ? document.substring(0, head + 6) + policy
                + document.substring(head + 6) : policy + document;
        web.loadDataWithBaseURL("about:blank", document, "text/html", "UTF-8", null);
        return web;
    }

    private static void save(Context context, ModelFileOutput.FileItem item) {
        OutputStream out = null;
        try {
            byte[] data = item.content.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, item.name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime(item.name));
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new java.io.IOException("Downloads insert failed");
                out = context.getContentResolver().openOutputStream(uri, "w");
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new java.io.IOException("Downloads unavailable");
                }
                out = new FileOutputStream(new File(dir, item.name), false);
            }
            if (out == null) throw new java.io.IOException("Output stream unavailable");
            out.write(data);
            out.flush();
            Toast.makeText(context, UiLanguage.text(context,
                    "已保存到下载目录", "Saved to Downloads"), Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(context, UiLanguage.text(context,
                    "保存失败：", "Save failed: ") + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
        }
    }

    private static String mime(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        return "text/plain";
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
