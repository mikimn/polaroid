# Looked up and called from native code (uvc_camera.cpp); renaming them would break JNI.
-keep class com.mikimn.droiduvc.NativeUvc { native <methods>; }
-keep class com.mikimn.droiduvc.ControlException { <init>(java.lang.String, int); }
