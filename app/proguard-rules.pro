# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable

# Keep custom View constructors
-keep class * extends android.view.View {
    public <init>(android.content.Context);
}
