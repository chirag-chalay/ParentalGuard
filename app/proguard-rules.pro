# ============================================================
# proguard-rules.pro
#
# ProGuard is a tool that SHRINKS, OPTIMIZES, and OBFUSCATES
# your compiled code before publishing a release APK.
#
# Shrinking    = removes unused classes and methods
# Optimization = rewrites bytecode to run faster
# Obfuscation  = renames classes/methods to short names like
#                "a", "b", "c" — making reverse-engineering harder
#
# These rules tell ProGuard what NOT to touch.
# Certain classes must keep their original names because:
#   - Firebase looks them up by name at runtime
#   - Android reflection requires original class names
#   - Crash reports need readable names
# ============================================================

# --- FIREBASE ---
# Keep all Firebase model classes intact.
# Firebase serializes/deserializes data using field names — if
# ProGuard renames them, data mapping will silently break.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# --- OUR APP MODELS ---
# Keep our own package classes (services, receivers etc.)
# so Android can find them by their declared names in the Manifest.
-keep class com.parentalguard.** { *; }

# --- KOTLIN ---
# Kotlin uses metadata annotations for reflection — keep them
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Keep Kotlin coroutines working correctly
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- ANDROID ---
# Keep classes referenced in AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep the Parcelable implementation (used by Android internals)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
