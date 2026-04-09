# Keep metadata used by Retrofit reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Keep public SDK entry points for binary/source compatibility.
-keep class com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager { *; }
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthRegion { *; }
-keep class com.tjlabs.tjlabsauth_sdk_android.TokenResult { *; }
-keep class com.tjlabs.tjlabsauth_sdk_android.TokenResult$* { *; }

# Gson in this SDK relies on field names (no @SerializedName), so keep DTO fields.
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthInput { <fields>; <init>(...); }
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthOutput { <fields>; <init>(...); }
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthClientMeta { <fields>; <init>(...); }
-keep class com.tjlabs.tjlabsauth_sdk_android.Sdk { <fields>; <init>(...); }

# Keep Retrofit interface methods and annotations.
-keep interface com.tjlabs.tjlabsauth_sdk_android.PostInput { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# R8 full mode: keep generic signatures used by Retrofit adapters.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**
