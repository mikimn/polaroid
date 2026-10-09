package com.mikimn.droiduvc

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UvcDevicesTest {

    private fun device(vararg interfaceClasses: Int): UsbDevice {
        val device = mock(UsbDevice::class.java)
        `when`(device.interfaceCount).thenReturn(interfaceClasses.size)
        interfaceClasses.forEachIndexed { index, cls ->
            val usbInterface = mock(UsbInterface::class.java)
            `when`(usbInterface.interfaceClass).thenReturn(cls)
            `when`(device.getInterface(index)).thenReturn(usbInterface)
        }
        return device
    }

    @Test
    fun `device with a video interface is UVC`() {
        assertTrue(device(UsbConstants.USB_CLASS_VIDEO).isUvc)
    }

    @Test
    fun `composite device with audio and video interfaces is UVC`() {
        assertTrue(device(UsbConstants.USB_CLASS_AUDIO, UsbConstants.USB_CLASS_VIDEO).isUvc)
    }

    @Test
    fun `device without a video interface is not UVC`() {
        assertFalse(device(UsbConstants.USB_CLASS_MASS_STORAGE, UsbConstants.USB_CLASS_HID).isUvc)
        assertFalse(device().isUvc)
    }

    @Test
    fun `uvcDevices filters the device list`() {
        val camera = device(UsbConstants.USB_CLASS_VIDEO)
        val storage = device(UsbConstants.USB_CLASS_MASS_STORAGE)
        val manager = mock(UsbManager::class.java)
        `when`(manager.deviceList).thenReturn(hashMapOf("a" to storage, "b" to camera))
        assertEquals(listOf(camera), manager.uvcDevices())
    }
}
