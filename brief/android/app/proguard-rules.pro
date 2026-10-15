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

# Classi di avvio e schermata di emergenza: nominate nel manifest e
# istanziate dal sistema.
-keep class com.lucio.brief.BriefApplication { *; }
-keep class com.lucio.brief.CrashActivity { *; }
-keep class com.lucio.brief.MainActivity { *; }

# I nomi originali nelle tracce di stack servono a capire i crash.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room e WorkManager.
# Room carica per nome le classi *_Impl che genera a compilazione
# (Class.forName("...WorkDatabase_Impl")). R8 le rimuoveva, e l'app moriva
# all'avvio dentro WorkManagerInitializer con
# "Failed to create an instance of androidx.work.impl.WorkDatabase",
# prima ancora che partisse Flutter. WorkManager arriva come dipendenza di
# Google Mobile Ads, quindi deve restare funzionante.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.room.migration.AutoMigrationSpec { <init>(); }
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-dontwarn androidx.room.**

# androidx.startup istanzia gli initializer per nome, leggendoli dai
# meta-data del manifest: i nomi non devono cambiare.
-keep class * implements androidx.startup.Initializer { *; }
