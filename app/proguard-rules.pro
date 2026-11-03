# Release shrinking rules. Based on NewPipe's rules for NewPipeExtractor.

## NewPipeExtractor loads time-ago patterns by reflection.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

## Rhino (used by NewPipeExtractor to run YouTube's player JS for signature and "n" deobfuscation).
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## Protobuf (used by NewPipeExtractor).
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

## OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

## kotlinx.serialization (navigation routes and InnerTube models).
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.freshtuber.**$$serializer { *; }
-keepclassmembers class app.freshtuber.** {
    *** Companion;
}
-keepclasseswithmembers class app.freshtuber.** {
    kotlinx.serialization.KSerializer serializer(...);
}
