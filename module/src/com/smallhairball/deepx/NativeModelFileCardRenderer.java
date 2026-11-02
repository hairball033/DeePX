package com.smallhairball.deepx;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

/** Renders generated files with DeepSeek's own upload-attachment Compose component. */
final class NativeModelFileCardRenderer {
    private static volatile Handles handles;
    private static volatile boolean unavailableLogged;

    private NativeModelFileCardRenderer() {}

    static void render(ClassLoader loader, Object composer,
            List<ModelFileOutput.StreamingFile> files) {
        if (loader == null || composer == null || files == null || files.isEmpty()) return;
        try {
            Handles present = handles;
            if (present == null || present.loader != loader) {
                present = new Handles(loader);
                handles = present;
            }
            int rendered = 0;
            for (ModelFileOutput.StreamingFile file : files) {
                if (file == null || file.name == null || file.name.length() == 0) continue;
                present.render(composer, file);
                if (++rendered >= 4) break;
            }
        } catch (Throwable error) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                Main.log("native model-file card unavailable: "
                        + Main.safeThrowableMessage(error));
            }
        }
    }

    static boolean renderToolLog(ClassLoader loader, Object composer, String text) {
        if (loader == null || composer == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        try {
            Handles present = handles;
            if (present == null || present.loader != loader) {
                present = new Handles(loader);
                handles = present;
            }
            present.renderToolLog(composer, text.trim());
            return true;
        } catch (Throwable error) {
            Main.log("native agent-tool card unavailable: "
                    + Main.safeThrowableMessage(error));
            return false;
        }
    }

    private static final class Handles {
        final ClassLoader loader;
        final Class<?> function2Type;
        final Constructor<?> composableLambdaConstructor;
        final Method attachmentCard;
        final Method attachmentTitle;
        final Method attachmentStatus;
        final Object modifier;
        final Object nativeFileIcon;
        final Object unit;

        Handles(ClassLoader loader) throws Exception {
            this.loader = loader;
            boolean play = HostCompat.isGooglePlay();
            boolean v236 = HostCompat.isV236();
            boolean v241 = HostCompat.isV241();
            Class<?> cardOwner = loader.loadClass(v241 ? "v01" : play ? "a11" : "hz0");
            Class<?> modifierType = loader.loadClass(v241 ? "mv5"
                    : play ? "zq5" : v236 ? "sp5" : "lp5");
            Class<?> backgroundType = loader.loadClass(v241 ? "ci0"
                    : play ? "si0" : "bh0");
            Class<?> lambdaType = loader.loadClass(v241 ? "zy1"
                    : play ? "cx1" : v236 ? "pv1" : "gv1");
            Class<?> clickType = loader.loadClass(v241 ? "il3"
                    : play ? "mi3" : v236 ? "rg3" : "ig3");
            function2Type = loader.loadClass(v241 ? "xl3"
                    : play ? "bj3" : v236 ? "gh3" : "xg3");
            Class<?> composerType = loader.loadClass(v241 ? "r12"
                    : play ? "uz1" : v236 ? "gy1" : "yx1");

            composableLambdaConstructor = lambdaType.getDeclaredConstructor(
                    Object.class, boolean.class, int.class);
            composableLambdaConstructor.setAccessible(true);
            attachmentCard = cardOwner.getDeclaredMethod("e",
                    modifierType, backgroundType, lambdaType, lambdaType, lambdaType,
                    clickType, function2Type, composerType, int.class, int.class);
            attachmentCard.setAccessible(true);
            attachmentTitle = cardOwner.getDeclaredMethod("g",
                    String.class, modifierType, composerType, int.class);
            attachmentTitle.setAccessible(true);
            attachmentStatus = cardOwner.getDeclaredMethod("f",
                    String.class, modifierType, boolean.class, boolean.class,
                    composerType, int.class, int.class);
            attachmentStatus.setAccessible(true);

            Field modifierField = loader.loadClass(v241 ? "jv5"
                    : play ? "wq5" : v236 ? "pp5" : "ip5")
                    .getDeclaredField("a");
            modifierField.setAccessible(true);
            modifier = modifierField.get(null);
            Field iconField = loader.loadClass(v241 ? "mba"
                    : play ? "lw8" : v236 ? "fn9" : "ym9")
                    .getDeclaredField(v241 ? "c" : play ? "g" : "h");
            iconField.setAccessible(true);
            nativeFileIcon = iconField.get(null);
            Field unitField = loader.loadClass(HostCompat.unitClass())
                    .getDeclaredField("a");
            unitField.setAccessible(true);
            unit = unitField.get(null);
        }

        void renderToolLog(Object composer, final String value) throws Exception {
            Object emptyTitle = composableLambdaConstructor.newInstance(
                    textLambda("", false), Boolean.FALSE, Integer.valueOf(0x544f4f31));
            Object status = composableLambdaConstructor.newInstance(
                    textLambda(value, true), Boolean.FALSE, Integer.valueOf(0x544f4f32));
            // The host attachment surface supplies its own rounded outline and compact padding.
            // Keeping the click and trailing slots defaulted also removes the large dead gaps.
            attachmentCard.invoke(null, modifier, null, nativeFileIcon,
                    emptyTitle, status, null, null, composer,
                    Integer.valueOf(1600902), Integer.valueOf(98));
        }

        void render(Object composer, final ModelFileOutput.StreamingFile file)
                throws Exception {
            Object title = composableLambdaConstructor.newInstance(
                    textLambda(file.name, false), Boolean.FALSE,
                    Integer.valueOf(0x4d464e01));
            boolean chinese = Locale.getDefault().getLanguage()
                    .toLowerCase(Locale.US).startsWith("zh");
            String statusText = file.complete
                    ? (chinese ? "已生成 · 点击查看" : "Ready · tap to open")
                    : (chinese ? "正在生成…" : "Generating…");
            Object status = composableLambdaConstructor.newInstance(
                    textLambda(statusText, true), Boolean.FALSE,
                    Integer.valueOf(0x4d464e02));
            Class<?> clickType = loader.loadClass(
                    HostCompat.isV241() ? "il3"
                    : HostCompat.isGooglePlay() ? "mi3" : "ig3");
            Object click = Proxy.newProxyInstance(loader,
                    new Class<?>[]{clickType}, new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("u".equals(method.getName())) {
                                NativeDualChatBridge.openLiveFile(file.name);
                                return unit;
                            }
                            return objectMethod(proxy, method, args);
                        }
                    });
            // Mask 66 keeps DeepSeek's own default background and optional trailing content while
            // preserving our click callback. All geometry, colours and iconography remain native.
            attachmentCard.invoke(null, modifier, null, nativeFileIcon,
                    title, status, click, null, composer,
                    Integer.valueOf(1600902), Integer.valueOf(66));
        }

        private Object textLambda(final String value, final boolean status) {
            return Proxy.newProxyInstance(loader,
                    new Class<?>[]{function2Type}, new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args)
                                throws Throwable {
                            if ("r".equals(method.getName()) && args != null
                                    && args.length == 2) {
                                if (status) {
                                    attachmentStatus.invoke(null, value, modifier,
                                            Boolean.FALSE, Boolean.FALSE, args[0],
                                            Integer.valueOf(48), Integer.valueOf(12));
                                } else {
                                    attachmentTitle.invoke(null, value, modifier,
                                            args[0], Integer.valueOf(0));
                                }
                                return unit;
                            }
                            return objectMethod(proxy, method, args);
                        }
                    });
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        if (method == null) return null;
        if ("toString".equals(method.getName())) return "DeePXNativeFileCardLambda";
        if ("hashCode".equals(method.getName())) return Integer.valueOf(
                System.identityHashCode(proxy));
        if ("equals".equals(method.getName())) return Boolean.valueOf(
                args != null && args.length == 1 && proxy == args[0]);
        return null;
    }
}
