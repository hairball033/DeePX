package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable JSON form renderer used by Java plugins. */
final class JavaPluginPageUi {
    private JavaPluginPageUi() {}

    static void show(Activity activity, String pluginId, JSONObject spec) {
        if (activity == null || activity.isFinishing()) return;
        new Page(activity, pluginId, spec == null ? new JSONObject() : spec).show();
    }

    private static final class Page {
        final Activity activity;
        final String pluginId;
        final JSONObject spec;
        final boolean dark;
        final int canvas, surface, inset, ink, secondary, muted, line, danger, success;
        final Dialog dialog;
        final LinearLayout fields;
        final LinearLayout actions;
        final TextView status;
        final ProgressBar progress;
        final Map<String, Binding> bindings = new LinkedHashMap<String, Binding>();
        final List<TextView> actionViews = new ArrayList<TextView>();

        Page(Activity activity, String pluginId, JSONObject spec) {
            this.activity = activity;
            this.pluginId = pluginId == null ? "" : pluginId;
            this.spec = spec;
            dark = DeePXUi.isDark(activity);
            canvas = dark ? 0xff191a1c : 0xfff6f7f8;
            surface = dark ? 0xff252629 : Color.WHITE;
            inset = dark ? 0xff202124 : 0xfff1f2f4;
            ink = dark ? 0xfff2f3f4 : 0xff1f2227;
            secondary = dark ? 0xffc9ccd1 : 0xff454a52;
            muted = dark ? 0xff969ba4 : 0xff777d87;
            line = dark ? 0xff3a3c41 : 0xffe2e4e8;
            danger = dark ? 0xffff8d8d : 0xffbf3b3b;
            success = dark ? 0xff82d2a2 : 0xff26794a;
            dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            fields = new LinearLayout(activity);
            actions = new LinearLayout(activity);
            status = text("", 12, muted, false);
            progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        }

        void show() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(canvas);

            LinearLayout bar = new LinearLayout(activity);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(8), statusBar(), dp(16), 0);
            TextView back = text("‹", 29, ink, false);
            back.setGravity(Gravity.CENTER);
            bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));
            LinearLayout titleBox = new LinearLayout(activity);
            titleBox.setOrientation(LinearLayout.VERTICAL);
            titleBox.addView(text(bound(spec.optString("title", "插件页面"), 80),
                    18, ink, true));
            String subtitle = bound(spec.optString("subtitle", ""), 160);
            if (subtitle.length() > 0) titleBox.addView(text(subtitle, 11, muted, false));
            bar.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
            root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56) + statusBar()));

            ScrollView scroll = new ScrollView(activity);
            fields.setOrientation(LinearLayout.VERTICAL);
            fields.setPadding(dp(16), dp(10), dp(16), dp(30));
            renderFields();
            scroll.addView(fields);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

            LinearLayout footer = new LinearLayout(activity);
            footer.setOrientation(LinearLayout.VERTICAL);
            footer.setPadding(dp(16), dp(10), dp(16), dp(12) + navigationBar());
            View divider = new View(activity);
            divider.setBackgroundColor(line);
            footer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            LinearLayout feedback = new LinearLayout(activity);
            feedback.setGravity(Gravity.CENTER_VERTICAL);
            feedback.setPadding(dp(2), dp(8), dp(2), dp(5));
            progress.setVisibility(View.GONE);
            feedback.addView(progress, new LinearLayout.LayoutParams(dp(18), dp(18)));
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, -2, 1);
            statusParams.leftMargin = dp(8);
            feedback.addView(status, statusParams);
            footer.addView(feedback);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            renderActions();
            footer.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));
            root.addView(footer);

            back.setOnClickListener(v -> dismiss(root));
            dialog.setContentView(root);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(canvas));
                window.setLayout(-1, -1);
            }
            dialog.show();
            root.setTranslationX(activity.getResources().getDisplayMetrics().widthPixels * .08f);
            root.setAlpha(0f);
            root.animate().translationX(0).alpha(1).setDuration(170).start();
        }

        void renderFields() {
            JSONArray list = spec.optJSONArray("fields");
            if (list == null || list.length() == 0) {
                fields.addView(text("这个插件页面没有声明字段。", 13, muted, false));
                return;
            }
            for (int i = 0; i < list.length() && i < 40; i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String id = safeId(item.optString("id", "field_" + i));
                if (id.length() == 0 || bindings.containsKey(id)) continue;
                String type = item.optString("type", "text").trim();
                LinearLayout block = new LinearLayout(activity);
                block.setOrientation(LinearLayout.VERTICAL);
                TextView label = text(bound(item.optString("label", id), 80),
                        13, secondary, true);
                block.addView(label);
                Binding binding = createBinding(id, type, item);
                LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, -2);
                controlParams.topMargin = dp(7);
                block.addView(binding.view, controlParams);
                String help = bound(item.optString("help", ""), 240);
                if (help.length() > 0) {
                    TextView hint = text(help, 11, muted, false);
                    hint.setLineSpacing(dp(1), 1f);
                    LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
                    hintParams.topMargin = dp(5);
                    block.addView(hint, hintParams);
                }
                bindings.put(id, binding);
                LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
                blockParams.bottomMargin = dp(17);
                fields.addView(block, blockParams);
            }
        }

        Binding createBinding(String id, String type, JSONObject item) {
            if ("toggle".equals(type)) {
                Switch value = new HubInsetSwitch(activity);
                value.setChecked(item.optBoolean("value", false));
                return new Binding(id, type, value);
            }
            if ("choice".equals(type)) {
                Spinner spinner = new Spinner(activity);
                JSONArray options = item.optJSONArray("options");
                ArrayList<String> values = new ArrayList<String>();
                if (options != null) for (int i = 0; i < options.length(); i++) {
                    values.add(bound(options.optString(i, ""), 80));
                }
                spinner.setAdapter(new ArrayAdapter<String>(activity,
                        android.R.layout.simple_spinner_dropdown_item, values));
                String selected = item.optString("value", "");
                int selectedIndex = values.indexOf(selected);
                if (selectedIndex >= 0) spinner.setSelection(selectedIndex);
                spinner.setBackground(round(inset, line, 11, 1));
                spinner.setPadding(dp(12), 0, dp(12), 0);
                return new Binding(id, type, spinner);
            }
            if ("image_challenge".equals(type)) {
                ChallengeView image = new ChallengeView(activity, inset, line, muted);
                image.setImage(item.optString("value", ""));
                image.setMinimumHeight(dp(176));
                return new Binding(id, type, image);
            }
            EditText value = new EditText(activity);
            value.setText(bound(item.optString("value", ""), 256 * 1024));
            value.setHint(bound(item.optString("hint", ""), 120));
            value.setHintTextColor(muted);
            value.setTextColor(ink);
            value.setTextSize(14);
            value.setPadding(dp(13), dp(11), dp(13), dp(11));
            value.setBackground(round(inset, line, 11, 1));
            if ("password".equals(type)) {
                value.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else if ("number".equals(type)) {
                value.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else if ("multiline".equals(type) || "output".equals(type)) {
                value.setGravity(Gravity.TOP | Gravity.START);
                value.setMinLines(item.optInt("minLines", "output".equals(type) ? 8 : 4));
                value.setMaxLines(Math.max(value.getMinLines(), item.optInt("maxLines", 20)));
                value.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            }
            if ("output".equals(type)) {
                value.setFocusable(false);
                value.setTextIsSelectable(true);
            }
            return new Binding(id, type, value);
        }

        void renderActions() {
            JSONArray list = spec.optJSONArray("actions");
            if (list == null || list.length() == 0) return;
            for (int i = 0; i < list.length() && i < 4; i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String id = safeId(item.optString("id", "action_" + i));
                String handler = safeId(item.optString("handler", id));
                TextView button = text(bound(item.optString("label", "确定"), 40),
                        14, "primary".equals(item.optString("style", "primary"))
                                ? Color.WHITE : ink, true);
                button.setGravity(Gravity.CENTER);
                boolean primary = "primary".equals(item.optString("style", "primary"));
                button.setBackground(round(primary ? DeePXUi.BRAND : inset,
                        primary ? DeePXUi.BRAND : line, 11, 1));
                button.setOnClickListener(v -> runAction(id, handler));
                actionViews.add(button);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                if (i > 0) params.leftMargin = dp(9);
                actions.addView(button, params);
            }
        }

        void runAction(String actionId, String handler) {
            JSONObject values = collectValues();
            setBusy(true, "正在处理…", muted);
            JSONObject request = new JSONObject();
            put(request, "actionId", actionId);
            put(request, "values", values);
            JavaPluginPlatform.invokeFormActionAsync(pluginId, handler, request, result ->
                    activity.runOnUiThread(() -> applyResult(result)));
        }

        JSONObject collectValues() {
            JSONObject values = new JSONObject();
            for (Binding binding : bindings.values()) put(values, binding.id, binding.value());
            return values;
        }

        void applyResult(JavaPluginApi.Result result) {
            if (result == null || !result.success) {
                String message = result == null ? "插件没有返回结果"
                        : (result.message.length() == 0 ? result.code : result.message);
                setBusy(false, message, danger);
                return;
            }
            JSONObject updates = result.data.optJSONObject("values");
            if (updates != null) {
                JSONArray names = updates.names();
                if (names != null) for (int i = 0; i < names.length(); i++) {
                    String id = names.optString(i, "");
                    Binding binding = bindings.get(id);
                    if (binding != null) binding.set(updates.opt(id));
                }
            }
            String message = bound(result.data.optString("message", "处理完成"), 240);
            setBusy(false, message, success);
            if (result.data.optBoolean("close", false)) dialog.dismiss();
        }

        void setBusy(boolean busy, String message, int color) {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            status.setText(message);
            status.setTextColor(color);
            for (TextView action : actionViews) {
                action.setEnabled(!busy);
                action.setAlpha(busy ? .55f : 1f);
            }
        }

        void dismiss(View root) {
            root.animate().translationX(root.getWidth()).alpha(0f).setDuration(145)
                    .withEndAction(dialog::dismiss).start();
        }

        TextView text(String value, float size, int color, boolean bold) {
            TextView view = new TextView(activity);
            view.setText(value);
            view.setTextSize(size);
            view.setTextColor(color);
            if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            return view;
        }

        GradientDrawable round(int fill, int stroke, float radius, int strokeDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(fill);
            drawable.setCornerRadius(dp(radius));
            drawable.setStroke(dp(strokeDp), stroke);
            return drawable;
        }

        int dp(float value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }

        int statusBar() {
            int id = activity.getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            return id > 0 ? activity.getResources().getDimensionPixelSize(id) : 0;
        }

        int navigationBar() {
            int id = activity.getResources().getIdentifier(
                    "navigation_bar_height", "dimen", "android");
            return id > 0 ? activity.getResources().getDimensionPixelSize(id) : 0;
        }
    }

    private static final class Binding {
        final String id, type;
        final View view;
        Binding(String id, String type, View view) {
            this.id = id; this.type = type; this.view = view;
        }
        Object value() {
            if (view instanceof EditText) return ((EditText) view).getText().toString();
            if (view instanceof Switch) return ((Switch) view).isChecked();
            if (view instanceof Spinner) return ((Spinner) view).getSelectedItem();
            if (view instanceof ChallengeView) return ((ChallengeView) view).value();
            return JSONObject.NULL;
        }
        void set(Object value) {
            if (view instanceof EditText) ((EditText) view).setText(
                    value == null || value == JSONObject.NULL ? "" : String.valueOf(value));
            else if (view instanceof Switch) ((Switch) view).setChecked(
                    value instanceof Boolean && (Boolean) value);
            else if (view instanceof Spinner) {
                android.widget.Spinner spinner = (android.widget.Spinner) view;
                String expected = value == null || value == JSONObject.NULL
                        ? "" : String.valueOf(value);
                android.widget.SpinnerAdapter adapter = spinner.getAdapter();
                if (adapter != null) for (int index = 0; index < adapter.getCount(); index++) {
                    if (expected.equals(String.valueOf(adapter.getItem(index)))) {
                        spinner.setSelection(index);
                        break;
                    }
                }
            }
            else if (view instanceof ChallengeView) ((ChallengeView) view).setImage(
                    value == null || value == JSONObject.NULL ? "" : String.valueOf(value));
        }
    }

    /** Image challenge field with normalized click coordinates returned to the plugin. */
    private static final class ChallengeView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int fill, border, hint;
        Bitmap bitmap;
        float clickX = -1f, clickY = -1f;
        final RectF target = new RectF();

        ChallengeView(Activity context, int fill, int border, int hint) {
            super(context); this.fill = fill; this.border = border; this.hint = hint;
            marker.setStyle(Paint.Style.STROKE);
            marker.setStrokeWidth(context.getResources().getDisplayMetrics().density * 1.5f);
            marker.setColor(0xffd64a4a);
            setClickable(true);
        }

        void setImage(String encoded) {
            bitmap = null; clickX = clickY = -1f;
            try {
                String raw = encoded == null ? "" : encoded.trim();
                int comma = raw.indexOf(',');
                if (raw.startsWith("data:") && comma >= 0) raw = raw.substring(comma + 1);
                if (raw.length() > 0) {
                    byte[] bytes = Base64.decode(raw, Base64.DEFAULT);
                    if (bytes.length <= 4 * 1024 * 1024) {
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                }
            } catch (Throwable ignored) {}
            invalidate();
        }

        JSONObject value() {
            JSONObject out = new JSONObject();
            put(out, "x", clickX);
            put(out, "y", clickY);
            return out;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.FILL); paint.setColor(fill);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 14, 14, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f); paint.setColor(border);
            canvas.drawRoundRect(.5f, .5f, getWidth() - .5f, getHeight() - .5f, 14, 14, paint);
            if (bitmap == null) {
                paint.setStyle(Paint.Style.FILL); paint.setColor(hint); paint.setTextSize(13f
                        * getResources().getDisplayMetrics().scaledDensity);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("等待图形验证", getWidth() / 2f, getHeight() / 2f, paint);
                return;
            }
            float scale = Math.min((getWidth() - 20f) / bitmap.getWidth(),
                    (getHeight() - 20f) / bitmap.getHeight());
            float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
            target.set((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                    (getWidth() + width) / 2f, (getHeight() + height) / 2f);
            canvas.drawBitmap(bitmap, null, target, paint);
            if (clickX >= 0f && clickY >= 0f) {
                float x = target.left + clickX * target.width();
                float y = target.top + clickY * target.height();
                canvas.drawCircle(x, y, 9f, marker);
                canvas.drawLine(x - 13f, y, x + 13f, y, marker);
                canvas.drawLine(x, y - 13f, x, y + 13f, marker);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP || bitmap == null
                    || !target.contains(event.getX(), event.getY())) return true;
            clickX = Math.max(0f, Math.min(1f,
                    (event.getX() - target.left) / target.width()));
            clickY = Math.max(0f, Math.min(1f,
                    (event.getY() - target.top) / target.height()));
            performClick(); invalidate(); return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
    }

    private static String safeId(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.matches("[A-Za-z0-9_.-]{1,80}") ? safe : "";
    }

    private static String bound(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); }
        catch (Throwable ignored) {}
    }
}
