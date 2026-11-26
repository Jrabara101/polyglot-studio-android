# Project-specific R8 / ProGuard keep rules for Polyglot Studio

# Preserve stack trace line numbers for crash triage
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Required by Firestore reflective deserialization and Kotlinx Serialization
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Keep Firestore data models (fields, getters/setters, no-arg constructors for DocumentSnapshot.toObject)
-keep class com.example.data.model.** { *; }

# Keep Navigation Compose @Serializable route objects and generated serializers
-keep class com.example.ui.navigation.** { *; }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
    *** INSTANCE;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}
