package com.smallhairball.deepx;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin facade over the encrypted patch-stream decoder.  The main DEX only knows the
 * {@link Delta} result shape; the channel-tracking logic that separates chain-of-thought from
 * public answer text lives inside the decrypted payload ({@code z12decoder}).
 */
public final class NativeApiPatchDecoder {
    public static final class Delta {
        public String text = "";
        public String reasoning = "";
        public String textSet;
        public String reasoningSet;
    }

    private static final String CORE = "com.smallhairball.deepx.z12decoder";
    private static Constructor<?> coreCtor;
    private static Method coreDecode;

    private final Object core;

    public NativeApiPatchDecoder() {
        Object instance = null;
        try {
            Constructor<?> ctor = coreConstructor();
            if (ctor != null) instance = ctor.newInstance();
        } catch (Throwable ignored) {}
        core = instance;
    }

    public Delta decode(Object json) {
        Delta out = new Delta();
        if (core == null) return out;
        try {
            Method m = decodeMethod();
            if (m != null) m.invoke(core, json, out);
        } catch (Throwable ignored) {}
        return out;
    }

    private static Constructor<?> coreConstructor() {
        if (coreCtor != null) return coreCtor;
        try {
            Class<?> cls = z14.payloadClass(null, CORE);
            if (cls == null) return null;
            Constructor<?> c = cls.getDeclaredConstructor();
            c.setAccessible(true);
            coreCtor = c;
            return c;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method decodeMethod() {
        if (coreDecode != null) return coreDecode;
        try {
            Class<?> cls = z14.payloadClass(null, CORE);
            if (cls == null) return null;
            for (Method m : cls.getDeclaredMethods()) {
                if ("decode".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    m.setAccessible(true);
                    coreDecode = m;
                    return m;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
