# VoiceDrop Proguard & R8 Rules
-keep class de.sciss.jump3r.** { *; }
-dontwarn javax.sound.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
-keepattributes *Annotation*
-keep class com.voicedrop.android.** { *; }

# Vosk and JNA Rules
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**
