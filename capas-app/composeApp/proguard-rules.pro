# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/joao.zao/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Compose specific rules
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }

# Koin
-keep class org.koin.** { *; }

# Ktor (if used)
-keep class io.ktor.** { *; }

# Serialization (if used)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.** { *; }

# Suppress warnings for JMX (not available on Android)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
