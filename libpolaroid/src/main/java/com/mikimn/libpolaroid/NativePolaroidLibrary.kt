package com.mikimn.libpolaroid

class NativePolaroidLibrary {

    /**
     * A native method that is implemented by the 'libpolaroid' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'libpolaroid' library on application startup.
        init {
            System.loadLibrary("libpolaroid")
        }
    }
}