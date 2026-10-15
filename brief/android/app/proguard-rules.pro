# FFmpegKit e whisper.cpp usano JNI: le classi caricate dal codice nativo
# non devono essere rinominate o rimosse.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.**

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# Plugin Flutter che usano la reflection per registrarsi.
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.embedding.** { *; }

# Flutter fa riferimento alle classi Play Core dei "deferred components",
# che non sono fra le dipendenze: senza queste regole R8 interrompe la
# build di release. L'app non usa i moduli differiti.
-dontwarn com.google.android.play.core.splitcompat.SplitCompatApplication
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**
-keep class io.flutter.embedding.engine.deferredcomponents.** { *; }
