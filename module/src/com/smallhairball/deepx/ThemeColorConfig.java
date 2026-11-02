package com.smallhairball.deepx;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class ThemeColorConfig {
    static final String HORIZONTAL = "horizontal";
    static final String VERTICAL = "vertical";
    static final String DIAGONAL_DOWN = "diagonal_down";
    static final String DIAGONAL_UP = "diagonal_up";
    private static final File FILE = new File(
            "/data/data/com.deepseek.chat/files/deekseep_theme_color.json");

    static final class Value {
        boolean enabled;
        int primary = 0xFF4D6BFE;
        boolean gradient;
        int secondary = 0xFF8B5CF6;
        String direction = HORIZONTAL;

        Value copy() {
            Value v = new Value();
            v.enabled = enabled;
            v.primary = primary;
            v.gradient = gradient;
            v.secondary = secondary;
            v.direction = direction;
            return v;
        }
    }

    private static volatile Value cached;

    static Value get() {
        Value v = cached;
        if (v != null) return v.copy();
        synchronized (ThemeColorConfig.class) {
            if (cached == null) cached = read();
            return cached.copy();
        }
    }

    static synchronized boolean save(Value v) {
        if (v == null) return false;
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", v.enabled);
            o.put("primary", String.format("#%08X", v.primary));
            o.put("gradient", v.gradient);
            o.put("secondary", String.format("#%08X", v.secondary));
            o.put("direction", validDirection(v.direction));
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File temp = new File(FILE.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(temp);
            out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            if (FILE.exists() && !FILE.delete()) return false;
            if (!temp.renameTo(FILE)) return false;
            cached = v.copy();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean setEnabled(boolean enabled) {
        Value v = get();
        v.enabled = enabled;
        return save(v);
    }

    private static Value read() {
        Value v = new Value();
        if (!FILE.isFile()) return v;
        try {
            FileInputStream in = new FileInputStream(FILE);
            byte[] data = new byte[(int) Math.min(FILE.length(), 32768L)];
            int count = in.read(data);
            in.close();
            if (count <= 0) return v;
            JSONObject o = new JSONObject(new String(data, 0, count, StandardCharsets.UTF_8));
            v.enabled = o.optBoolean("enabled", false);
            v.primary = parseColor(o.optString("primary"), v.primary);
            v.gradient = o.optBoolean("gradient", false);
            v.secondary = parseColor(o.optString("secondary"), v.secondary);
            v.direction = validDirection(o.optString("direction", HORIZONTAL));
        } catch (Throwable ignored) {
        }
        return v;
    }

    static int parseColor(String text, int fallback) {
        if (text == null) return fallback;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return (int) (0xFF000000L | Long.parseLong(s, 16));
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static String validDirection(String direction) {
        if (VERTICAL.equals(direction) || DIAGONAL_DOWN.equals(direction)
                || DIAGONAL_UP.equals(direction)) return direction;
        return HORIZONTAL;
    }
}
