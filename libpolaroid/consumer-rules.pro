# Looked up and called from native code (uvc_camera.cpp); renaming them would break JNI.
-keep class com.mikimn.libpolaroid.NativeUvc { native <methods>; }
-keep class com.mikimn.libpolaroid.ControlException { <init>(java.lang.String, int); }
