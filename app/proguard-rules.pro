# Add project specific ProGuard rules here.
-keep class com.dalur.film.** { *; }
-keep class org.wysaid.** { *; }
-dontwarn org.wysaid.**
-dontwarn org.maplibre.**
-keepclasseswithmembernames class * {
    native <methods>;
}
