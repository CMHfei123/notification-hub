# NotificationHub ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class com.navigationhub.** { *; }
-dontwarn com.navigationhub.**
-dontwarn okhttp3.**
-dontwarn okio.**
