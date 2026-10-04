# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.fca.player.**$$serializer { *; }
-keepclassmembers class app.fca.player.** { *** Companion; }
-keepclasseswithmembers class app.fca.player.** { kotlinx.serialization.KSerializer serializer(...); }
