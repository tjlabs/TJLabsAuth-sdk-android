# Rules applied in host app shrink phase when this SDK is consumed as AAR.

# Keep metadata required by Retrofit reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Keep public API symbols that host app calls directly.
-keep class com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager { *; }
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthRegion { *; }
-keep class com.tjlabs.tjlabsauth_sdk_android.TokenResult { *; }
-keep class com.tjlabs.tjlabsauth_sdk_android.TokenResult$* { *; }

# Keep DTO field names for Gson request/response mapping.
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthInput { <fields>; <init>(...); }
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthOutput { <fields>; <init>(...); }
-keep class com.tjlabs.tjlabsauth_sdk_android.AuthClientMeta { <fields>; <init>(...); }
-keep class com.tjlabs.tjlabsauth_sdk_android.Sdk { <fields>; <init>(...); }

# Keep Retrofit service interface used internally by the SDK.
-keep interface com.tjlabs.tjlabsauth_sdk_android.PostInput { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**
