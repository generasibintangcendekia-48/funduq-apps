# 1. Keep Kotlin Standard & Coroutines
-keep class kotlin.** { *; }
-keep class kotlin.text.** { *; }
-dontwarn kotlin.**

# 2. Keep Data Models & Package Aplikasi
-keep class com.funduq.app.** { *; }
-keepclassmembers class com.funduq.app.** { *; }

# 3. Keep Firebase & Firestore
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# 4. Keep Jetpack Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
