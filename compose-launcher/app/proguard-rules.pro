# Open edition: do NOT obfuscate any names (keep 100% transparent and readable),
# but allow R8 to discard unused third-party Compose and AndroidX classes.
-dontobfuscate

# Module code is reached through Xposed entry points, Android components, reflection, JNI-style
# protocol names, and host callbacks. Keep it byte-for-byte addressable while allowing R8 to
# discard only unreachable third-party library code.
-keep class com.smallhairball.deepx.** { *; }
-keep interface com.smallhairball.deepx.** { *; }


-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod