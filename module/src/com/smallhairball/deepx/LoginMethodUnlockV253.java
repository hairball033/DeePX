package com.smallhairball.deepx;

import com.smallhairball.deepx.LegacyXposedModule.Chain;
import com.smallhairball.deepx.LegacyXposedModule.Hooker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 2.5.3 login-entry unlock.
 *
 * <p>The host builds the visible login-method list from a network/region probe. 2.5.3 symbols:
 * state {@code h76}, its field {@code b} = {@code List<s66>}; constants {@code s66.a} = Google,
 * {@code s66.b} = SMS, {@code s66.c} = one-tap mobile, {@code s66.d} = password/email,
 * {@code s66.e} = register, {@code s66.f} = WeChat. A US exit node yields
 * {@code [google, password, register]} while a mainland network yields
 * {@code [one-tap, wechat, sms, password]}, so each region hides the other side's entries.
 *
 * <p>This installer hooks the state constructors and every static state-copy method and merges the
 * entries both switches ask for, so "unlock WeChat + mobile" and "unlock Google + email" can be on
 * at the same time and remain independent of the network/region probe.
 */
final class LoginMethodUnlockV253 {
    private LoginMethodUnlockV253() {}

    private static volatile boolean logged = false;
    private static volatile long switchesReadAt = 0L;
    private static volatile boolean googleOn = false;
    private static volatile boolean wechatMobileOn = false;

    static void install(ClassLoader loader) {
        try {
            final Class<?> stateType = HostCompat.load(loader, "h76");
            final Class<?> optionType = HostCompat.load(loader, "s66");
            final Object google = readOption(optionType, "a");
            final Object sms = readOption(optionType, "b");
            final Object oneTap = readOption(optionType, "c");
            final Object password = readOption(optionType, "d");
            final Object register = readOption(optionType, "e");
            final Object wechat = readOption(optionType, "f");
            int constructors = 0;
            int copies = 0;
            for (Constructor<?> ctor : stateType.getDeclaredConstructors()) {
                final int listIndex = findListIndex(ctor.getParameterTypes());
                if (listIndex < 0) continue;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return proceed(chain, listIndex, google, sms, oneTap, password,
                                register, wechat, optionType);
                    }
                });
                constructors++;
            }
            for (Method method : stateType.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != stateType) continue;
                final int listIndex = findListIndex(method.getParameterTypes());
                if (listIndex < 0) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return proceed(chain, listIndex, google, sms, oneTap, password,
                                register, wechat, optionType);
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                copies++;
            }
            Main.log("hooked 2.5.3 login options: h76 ctors=" + constructors
                    + ", copies=" + copies + ", google="
                    + HookAccountLoginSecurity.isGoogleLoginUnlock()
                    + ", wechatMobile=" + HookAccountLoginSecurity.isWechatMobileLoginUnlock());
        } catch (Throwable t) {
            Main.log("LoginMethodUnlockV253 install failed: " + t);
        }
    }

    private static Object readOption(Class<?> optionType, String name) throws Throwable {
        Field field = optionType.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static int findListIndex(Class<?>[] types) {
        if (types == null) return -1;
        for (int i = 0; i < types.length; i++) {
            if (List.class.isAssignableFrom(types[i])) return i;
        }
        return -1;
    }

    private static Object proceed(Chain chain, int listIndex, Object google, Object sms,
            Object oneTap, Object password, Object register, Object wechat,
            Class<?> optionType) throws Throwable {
        long now = System.currentTimeMillis();
        if (now - switchesReadAt > 1000L) {
            googleOn = HookAccountLoginSecurity.isGoogleLoginUnlock();
            wechatMobileOn = HookAccountLoginSecurity.isWechatMobileLoginUnlock();
            switchesReadAt = now;
        }
        if (!googleOn && !wechatMobileOn) return chain.proceed();
        try {
            Object[] args = chain.getArgs().toArray();
            List<?> original = args[listIndex] instanceof List ? (List<?>) args[listIndex] : null;
            List<?> unlocked = original;
            if (googleOn) {
                unlocked = ensureMethods(unlocked,
                        new Object[]{google, password, register}, optionType);
            }
            if (wechatMobileOn) {
                unlocked = ensureMethods(unlocked,
                        new Object[]{wechat, oneTap, sms}, optionType);
            }
            if (unlocked != null && unlocked != original) {
                args[listIndex] = unlocked;
                if (!logged) {
                    logged = true;
                    Main.log("2.5.3 login options merged: original=" + original.size()
                            + ", unlocked=" + unlocked.size());
                }
                return chain.proceed(args);
            }
        } catch (Throwable t) {
            Main.log("2.5.3 login option injection skipped: " + t);
        }
        return chain.proceed();
    }

    /**
     * Appends the entries missing from the host list. Empty lists are loading placeholders and are
     * left untouched, and a list holding foreign elements is passed through unchanged.
     */
    static List<?> ensureMethods(Object raw, Object[] required, Class<?> optionType) {
        if (!(raw instanceof List) || required == null || optionType == null) {
            return raw instanceof List ? (List<?>) raw : null;
        }
        List<?> source = (List<?>) raw;
        if (source.isEmpty()) return source;
        for (Object item : source) {
            if (item != null && !optionType.isInstance(item)) return source;
        }
        ArrayList<Object> unlocked = null;
        for (Object want : required) {
            if (want == null || contains(source, want)) continue;
            if (unlocked != null && contains(unlocked, want)) continue;
            if (unlocked == null) unlocked = new ArrayList<Object>(source);
            unlocked.add(want);
        }
        return unlocked == null ? source : unlocked;
    }

    private static boolean contains(List<?> list, Object want) {
        for (Object item : list) {
            if (item == want) return true;
            if (item != null && item.equals(want)) return true;
        }
        return false;
    }
}
