# No-obfuscation R8 profile: let R8 shrink and optimize, but keep every name readable so the
# module remains transparent, Xposed-addressable and reviewable.
-dontobfuscate
-dontwarn
-keepattributes SourceFile
-renamesourcefileattribute SourceFile
