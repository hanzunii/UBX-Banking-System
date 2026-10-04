# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.atm.mobile.model.** { *; }
-keep class com.atm.mobile.network.** { *; }
-keep class org.json.** { *; }
-keepclassmembers class * implements android.os.Parcelable { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes InnerClasses,EnclosingMethod
-keep class com.squareup.okhttp3.** { *; }
-keep interface com.squareup.okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn javax.annotation.**

# ZXing / barcode scanner
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }