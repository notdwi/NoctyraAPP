-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.noctyra.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.noctyra.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.noctyra.app.**$$serializer { *; }

-dontwarn com.google.re2j.**

-keep class com.discord.** { *; }
-keep class org.webrtc.** { *; }
-keep class com.noctyra.app.discord.DiscordBridge {
    native <methods>;
    public void on*(...);
}

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
