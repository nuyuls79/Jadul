# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================
# KOTLIN RULES
# ============================================
# Keep Kotlin metadata
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Keep Kotlin reflection
-keep class kotlin.reflect.** { *; }

# ============================================
# ANDROIDX & SUPPORT LIBRARIES
# ============================================
# Keep AndroidX classes
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Keep support library classes
-keep class android.support.** { *; }
-keep interface android.support.** { *; }
-dontwarn android.support.**

# Keep appcompat
-keep class android.support.v7.app.** { *; }
-keep class androidx.appcompat.** { *; }

# ============================================
# DATA BINDING RULES
# ============================================
# Data binding
-dontwarn android.databinding.**
-keep class android.databinding.** { *; }
-keep class androidx.databinding.** { *; }

# Keep generated data binding classes
-keep class net.harimurti.tv.databinding.** { *; }
-keep class net.harimurti.tv.BR { *; }

# ============================================
# EXOPLAYER RULES
# ============================================
# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ExoPlayer extensions
-keep class com.google.android.exoplayer2.ext.** { *; }
-dontwarn com.google.android.exoplayer2.ext.**

# ============================================
# RTMP RULES
# ============================================
# RTMP client
-keep class io.antmedia.rtmp.** { *; }
-dontwarn io.antmedia.rtmp.**
-keep class net.butterflytv.utils.** { *; }
-dontwarn net.butterflytv.utils.**

# ============================================
# OKHTTP RULES
# ============================================
# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================
# GSON RULES
# ============================================
# Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.examples.android.model.** { *; }
-dontwarn com.google.gson.**

# Keep your model classes for Gson
-keep class net.harimurti.tv.model.** { *; }
-keep class net.harimurti.tv.extra.** { *; }

# ============================================
# GOOGLE PLAY SERVICES RULES
# ============================================
# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ============================================
# SHIMMER RULES
# ============================================
# Facebook Shimmer
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# ============================================
# VERTICAL SEEK BAR RULES
# ============================================
# VerticalSeekBar
-keep class com.h6ah4i.android.widget.verticalseekbar.** { *; }
-dontwarn com.h6ah4i.android.widget.verticalseekbar.**

# ============================================
# FILE PICKER RULES
# ============================================
# FilePicker
-keep class com.github.TutorialsAndroid.FilePicker.** { *; }
-dontwarn com.github.TutorialsAndroid.FilePicker.**

# ============================================
# YOUR APP SPECIFIC RULES
# ============================================
# Keep your main classes
-keep class net.harimurti.tv.** { *; }
-keep class net.harimurti.tv.MainActivity { *; }
-keep class net.harimurti.tv.PlayerActivity { *; }

# Keep your adapters
-keep class net.harimurti.tv.adapter.** { *; }

# Keep your fragments/dialogs
-keep class net.harimurti.tv.dialog.** { *; }

# Keep your extensions
-keep class net.harimurti.tv.extension.** { *; }

# ============================================
# GENERAL ANDROID RULES
# ============================================
# Keep custom view classes
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    void set*(***);
    *** get*();
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keep class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    *** writeReplace();
    *** readResolve();
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R classes
-keep class **.R$* { *; }

# Keep Javascript interfaces for WebView
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ============================================
# DEBUGGING OPTIONS
# ============================================
# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide original source file name (optional)
# -renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*, Signature, Exception

# ============================================
# REMOVE WARNINGS
# ============================================
# Suppress various warnings
-dontwarn javax.**
-dontwarn java.awt.**
-dontwarn org.codehaus.**
-dontwarn org.apache.**
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.**
