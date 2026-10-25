# Proguard rules for Kugou Concept Music App
-keep class com.concept.music.data.model.** { *; }
-keep class com.concept.music.data.kugou.SavedAccount { *; }
-keep class com.concept.music.data.kugou.KugouSession { *; }
-keep class com.concept.music.data.kugou.DeviceIdentity { *; }
-keep class com.concept.music.ui.viewmodel.SavedPlayQueue { *; }
-keep class androidx.media3.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
