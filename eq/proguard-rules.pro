# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.fca.eq.**$$serializer { *; }
-keepclassmembers class app.fca.eq.** { *** Companion; }
-keepclasseswithmembers class app.fca.eq.** { kotlinx.serialization.KSerializer serializer(...); }
