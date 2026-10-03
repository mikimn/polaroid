package com.mikimn.libpolaroid

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/** True if the device exposes a USB Video Class interface. */
val UsbDevice.isUvc: Boolean
    get() = (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }

fun UsbManager.uvcDevices(): List<UsbDevice> = deviceList.values.filter { it.isUvc }
