package com.smallhairball.deepx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared reflection and bounded diagnostic helpers kept out of the hook coordinator. */
public abstract class MainReflectionSupport extends LegacyXposedModule {
    public static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    public static boolean isFunction2(Object value) {
        if (value == null) return false;
        for (Class<?> itf : allInterfaces(value.getClass())) {
            for (Method method : itf.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 2 && !isObjectMethod(method)) return true;
            }
        }
        return false;
    }

    public static Object objectMethod(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("toString".equals(name)) return "VPProxy@" + System.identityHashCode(proxy);
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("equals".equals(name)) {
            return proxy == (args != null && args.length > 0 ? args[0] : null);
        }
        return null;
    }

    public static List<Class<?>> allInterfaces(Class<?> type) {
        LinkedHashSet<Class<?>> out = new LinkedHashSet<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            collectInterfaces(current, out);
        }
        return new ArrayList<Class<?>>(out);
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> out) {
        for (Class<?> itf : type.getInterfaces()) {
            if (out.add(itf)) collectInterfaces(itf, out);
        }
    }

    public static String summarizeFlowEvent(Object value) {
        if (value == null) return "null";
        String name = simpleName(value);
        if (HostCompat.simpleNameIs(value, "lv7")) {
            return "lv7{event=" + logValue(fieldByName(value, "a"))
                    + ", data=" + logValue(fieldByName(value, "b")) + "}";
        }
        String network = summarizeNetworkResult(value);
        return network != null ? name + " " + network : deepDump(value, 3);
    }

    public static String deepDump(Object value, int depth) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return logValue(value);
        }
        if (value instanceof List || value instanceof Map || value instanceof android.net.Uri) {
            return logValue(value);
        }
        String name = simpleName(value);
        if (depth <= 0) return name + "(" + truncateForLog(String.valueOf(value), 80) + ")";
        StringBuilder out = new StringBuilder(name).append('{');
        int count = 0;
        for (Field field : value.getClass().getDeclaredFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (count > 0) out.append(", ");
                out.append(field.getName()).append('=')
                        .append(deepDump(field.get(value), depth - 1));
                if (++count >= 16) { out.append(", ..."); break; }
            } catch (Throwable ignored) {}
        }
        return out.append('}').toString();
    }

    private static String summarizeNetworkResult(Object result) {
        if (result == null || HostCompat.simpleNameIs(result, "w02")) return null;
        if (HostCompat.simpleNameIs(result, "kp5")) {
            Object business = fieldByName(result, "a");
            Object data = fieldByName(result, "b");
            if (HostCompat.simpleNameIs(data, "fp") || "ul6".equals(simpleName(data))
                    || HostCompat.simpleNameIs(business, "vx2")) {
                return "ok biz=" + logValue(business) + " data=" + logValue(data);
            }
        }
        if (HostCompat.simpleNameIs(result, "op5")) {
            Object business = fieldByName(result, "a");
            if (HostCompat.simpleNameIs(business, "vx2")) {
                return "err biz=" + logValue(business)
                        + " msg=" + logValue(fieldByName(result, "b"))
                        + " detail=" + logValue(fieldByName(result, "c"));
            }
        }
        return null;
    }

    public static String summarizeFp(Object file) {
        if (file == null) return "null";
        return "fp{file_id=" + logValue(fieldByName(file, "a"))
                + ", status=" + logValue(fieldByName(file, "b"))
                + ", name=" + logValue(fieldByName(file, "c"))
                + ", size=" + logValue(fieldByName(file, "d"))
                + ", inserted_at=" + logValue(fieldByName(file, "e"))
                + ", updated_at=" + logValue(fieldByName(file, "f"))
                + ", token_usage=" + logValue(fieldByName(file, "g"))
                + ", previewable=" + logValue(fieldByName(file, "h"))
                + ", from_share=" + logValue(fieldByName(file, "i"))
                + ", signed_path=" + logValue(fieldByName(file, "j"))
                + ", is_image=" + logValue(fieldByName(file, "k"))
                + ", audit_result=" + logValue(fieldByName(file, "l"))
                + ", width=" + logValue(fieldByName(file, "m"))
                + ", height=" + logValue(fieldByName(file, "n"))
                + ", retryable=" + logValue(fieldByName(file, "o")) + "}";
    }

    public static Object fieldByName(Object value, String name) {
        if (value == null) return null;
        name = HostCompat.staticMessageField(value, name);
        try {
            Field field = value.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(value);
        } catch (Throwable ignored) { return null; }
    }

    public static String logValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String text = (String) value;
            return "String(len=" + text.length() + ", \""
                    + truncateForLog(text, 320) + "\")";
        }
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof List) {
            List list = (List) value;
            StringBuilder out = new StringBuilder("List(size=").append(list.size()).append(", [");
            for (int i = 0; i < list.size() && i < 6; i++) {
                if (i > 0) out.append(", ");
                out.append(logValue(list.get(i)));
            }
            if (list.size() > 6) out.append(", ...");
            return out.append("])").toString();
        }
        if (value instanceof Map) return "Map(size=" + ((Map) value).size() + ")";
        if (value instanceof android.net.Uri) {
            return "Uri(" + truncateForLog(String.valueOf(value), 200) + ")";
        }
        String name = simpleName(value);
        if (HostCompat.simpleNameIs(value, "fp")) return summarizeFp(value);
        if ("ul6".equals(name)) return "ul6{files=" + logValue(fieldByName(value, "a")) + "}";
        if (HostCompat.simpleNameIs(value, "jv0")) return String.valueOf(value);
        return name + "(" + truncateForLog(String.valueOf(value), 160) + ")";
    }

    public static String truncateForLog(String value, int maximum) {
        if (value == null) return "null";
        String singleLine = value.replace('\n', ' ').replace('\r', ' ');
        if (singleLine.length() <= maximum) return singleLine;
        return singleLine.substring(0, maximum) + "...<len=" + singleLine.length() + ">";
    }

    public static String simpleName(Object value) {
        if (value == null) return "null";
        String name = value instanceof Class ? ((Class<?>) value).getName()
                : value.getClass().getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
