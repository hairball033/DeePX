package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ABI v2 bridge for every concrete Member installed by the unified Xposed adapter.
 *
 * <p>The bridge exposes metadata rather than live host objects. This keeps callbacks stable across
 * host versions and prevents an asynchronous plugin callback from retaining a transient
 * MethodHookParam. Every installed member has four independently subscribable phases:
 * registered, before, after and error.</p>
 */
final class JavaPluginRuntimeBridge {
    static final String PREFIX = "host.member.";
    static final String[] PHASES = {"registered", "before", "after", "error"};
    private static final Map<String, Descriptor> MEMBERS = new LinkedHashMap<>();

    static final class Descriptor {
        final String baseId, owner, name, signature, returnType, sourceHook;
        final int sourceLine;
        final List<String> parameterTypes;
        final boolean constructor, isStatic;

        Descriptor(String baseId, String owner, String name, String signature,
                   String returnType, List<String> parameterTypes,
                   boolean constructor, boolean isStatic, String sourceHook, int sourceLine) {
            this.baseId = baseId;
            this.owner = owner;
            this.name = name;
            this.signature = signature;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.constructor = constructor;
            this.isStatic = isStatic;
            this.sourceHook = sourceHook == null ? "" : sourceHook;
            this.sourceLine = sourceLine;
        }
    }

    private JavaPluginRuntimeBridge() {}

    static void registered(Member member) {
        String[] source = findSourceCaller();
        Descriptor descriptor = describe(member, source[0], parseLine(source[1]));
        if (descriptor == null) return;
        boolean added;
        synchronized (MEMBERS) {
            added = !MEMBERS.containsKey(descriptor.baseId);
            if (added) MEMBERS.put(descriptor.baseId, descriptor);
        }
        if (added) JavaPluginManager.emitRuntime(
                descriptor.baseId + ".registered", event(descriptor, "registered"));
    }

    static void before(Member member, Object receiver, Object[] arguments) {
        if (!JavaPluginManager.hasRuntimeSubscribers()) return;
        Descriptor descriptor = ensure(member);
        if (descriptor == null) return;
        JSONObject data = event(descriptor, "before");
        put(data, "receiverType", receiver == null ? "" : receiver.getClass().getName());
        put(data, "argumentCount", arguments == null ? 0 : arguments.length);
        put(data, "argumentTypes", typeArray(arguments));
        JavaPluginManager.emitRuntime(descriptor.baseId + ".before", data);
    }

    static void after(Member member, Object result, long durationNanos) {
        if (!JavaPluginManager.hasRuntimeSubscribers()) return;
        Descriptor descriptor = ensure(member);
        if (descriptor == null) return;
        JSONObject data = event(descriptor, "after");
        put(data, "resultType", result == null ? "" : result.getClass().getName());
        put(data, "durationUs", Math.max(0L, durationNanos / 1000L));
        JavaPluginManager.emitRuntime(descriptor.baseId + ".after", data);
    }

    static void error(Member member, Throwable error, long durationNanos) {
        if (!JavaPluginManager.hasRuntimeSubscribers()) return;
        Descriptor descriptor = ensure(member);
        if (descriptor == null) return;
        JSONObject data = event(descriptor, "error");
        put(data, "errorType", error == null ? "" : error.getClass().getName());
        put(data, "errorMessage", bounded(error == null ? "" : error.getMessage(), 512));
        put(data, "durationUs", Math.max(0L, durationNanos / 1000L));
        JavaPluginManager.emitRuntime(descriptor.baseId + ".error", data);
    }

    static List<Descriptor> descriptors() {
        synchronized (MEMBERS) {
            return Collections.unmodifiableList(new ArrayList<>(MEMBERS.values()));
        }
    }

    static Descriptor findPhase(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        for (String phase : PHASES) {
            String suffix = "." + phase;
            if (!id.endsWith(suffix)) continue;
            synchronized (MEMBERS) {
                return MEMBERS.get(id.substring(0, id.length() - suffix.length()));
            }
        }
        return null;
    }

    static void publishRegisteredSnapshot() {
        for (Descriptor descriptor : descriptors()) {
            JavaPluginManager.emitRuntime(descriptor.baseId + ".registered",
                    event(descriptor, "registered"));
        }
    }

    /** Exercises all four phase routes without touching a host method. */
    static void emitSelfTestPhases() {
        try {
            Method method = JavaPluginRuntimeBridge.class.getDeclaredMethod(
                    "selfTestTarget", String.class);
            registered(method);
            before(method, null, new Object[]{"test"});
            after(method, "ok", 1000L);
            error(method, new IllegalStateException("expected-self-test"), 2000L);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unused")
    private static String selfTestTarget(String value) { return value; }

    private static Descriptor ensure(Member member) {
        Descriptor descriptor = describe(member, "", 0);
        if (descriptor == null) return null;
        synchronized (MEMBERS) {
            Descriptor existing = MEMBERS.get(descriptor.baseId);
            if (existing != null) return existing;
            MEMBERS.put(descriptor.baseId, descriptor);
        }
        return descriptor;
    }

    private static Descriptor describe(Member member) {
        return describe(member, "", 0);
    }

    private static Descriptor describe(Member member, String sourceHook, int sourceLine) {
        if (member == null || member.getDeclaringClass() == null) return null;
        try {
            boolean constructor = member instanceof Constructor;
            Class<?>[] parameters;
            String returnType;
            if (member instanceof Method) {
                parameters = ((Method) member).getParameterTypes();
                returnType = ((Method) member).getReturnType().getName();
            } else if (member instanceof Constructor) {
                parameters = ((Constructor<?>) member).getParameterTypes();
                returnType = member.getDeclaringClass().getName();
            } else {
                return null;
            }
            String owner = member.getDeclaringClass().getName();
            String name = constructor ? "init" : member.getName();
            ArrayList<String> parameterTypes = new ArrayList<>();
            StringBuilder signature = new StringBuilder(owner).append('#').append(name).append('(');
            for (int index = 0; index < parameters.length; index++) {
                if (index > 0) signature.append(',');
                String type = parameters[index].getName();
                parameterTypes.add(type);
                signature.append(type);
            }
            signature.append("):").append(returnType);
            String ownerPart = clean(member.getDeclaringClass().getSimpleName());
            String namePart = clean(name);
            String baseId = PREFIX + ownerPart + "." + namePart + "."
                    + Long.toHexString(fnv1a64(signature.toString()));
            return new Descriptor(baseId, owner, name, signature.toString(), returnType,
                    Collections.unmodifiableList(parameterTypes), constructor,
                    Modifier.isStatic(member.getModifiers()), sourceHook, sourceLine);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JSONObject event(Descriptor descriptor, String phase) {
        JSONObject data = new JSONObject();
        put(data, "phase", phase);
        put(data, "memberId", descriptor.baseId);
        put(data, "owner", descriptor.owner);
        put(data, "name", descriptor.name);
        put(data, "signature", descriptor.signature);
        put(data, "returnType", descriptor.returnType);
        put(data, "parameterTypes", new JSONArray(descriptor.parameterTypes));
        put(data, "constructor", descriptor.constructor);
        put(data, "static", descriptor.isStatic);
        put(data, "sourceHook", descriptor.sourceHook);
        put(data, "sourceLine", descriptor.sourceLine);
        return data;
    }

    private static JSONArray typeArray(Object[] values) {
        JSONArray array = new JSONArray();
        if (values != null) for (Object value : values) {
            array.put(value == null ? JSONObject.NULL : value.getClass().getName());
        }
        return array;
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (Throwable ignored) {}
    }

    private static String clean(String value) {
        String safe = value == null ? "member" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_$]+", "_");
        return safe.length() == 0 ? "member" : safe;
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private static String[] findSourceCaller() {
        String mainName = Main.class.getName();
        try {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (!mainName.equals(element.getClassName())) continue;
                String method = element.getMethodName();
                if (method == null || method.length() == 0
                        || "registered".equals(method) || "describe".equals(method)
                        || "ensure".equals(method)) continue;
                return new String[]{method, String.valueOf(element.getLineNumber())};
            }
        } catch (Throwable ignored) {}
        return new String[]{"", "0"};
    }

    private static int parseLine(String value) {
        try { return Integer.parseInt(value); }
        catch (Throwable ignored) { return 0; }
    }
    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        byte[] bytes;
        try { bytes = value.getBytes("UTF-8"); }
        catch (Throwable ignored) { bytes = value.getBytes(); }
        for (byte item : bytes) {
            hash ^= item & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
