# GuardianSpace ProGuard Rules

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep BlackBox engine classes
-keep class com.tools.vspace.blackbox.** { *; }
-keep class com.tools.vspace.engine.** { *; }

# Keep GG Patcher
-keep class com.tools.vspace.engine.patch.GG_Patcher { *; }
-keep class com.tools.vspace.engine.patch.RootEmu { *; }

# Keep VirtualCore
-keep class com.tools.vspace.VirtualCore { *; }

# Keep Build spoofing fields
-keep class android.os.Build { *; }
-keep class android.os.Build$VERSION { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Material Design
-keep class com.google.android.material.** { *; }
