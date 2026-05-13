# BioAsistencia ProGuard Rules
-keep class com.bioasistencia.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
