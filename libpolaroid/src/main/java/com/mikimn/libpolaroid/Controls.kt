package com.mikimn.libpolaroid

import java.io.IOException

/** A UVC control transfer failed. [reason] summarizes the underlying libusb error [code]. */
class ControlException(message: String, val code: Int) : IOException(message) {
    enum class Reason {
        /** The device stalled the request: the control is unsupported or the value is invalid. */
        UNSUPPORTED_OR_INVALID,

        /** The device was unplugged. */
        DISCONNECTED,
        TIMEOUT,
        OTHER,
    }

    val reason: Reason
        get() = when (code) {
            LIBUSB_ERROR_PIPE -> Reason.UNSUPPORTED_OR_INVALID
            LIBUSB_ERROR_NO_DEVICE -> Reason.DISCONNECTED
            LIBUSB_ERROR_TIMEOUT -> Reason.TIMEOUT
            else -> Reason.OTHER
        }

    private companion object {
        const val LIBUSB_ERROR_TIMEOUT = -7
        const val LIBUSB_ERROR_PIPE = -9
        const val LIBUSB_ERROR_NO_DEVICE = -4
    }
}

/** The controls a UVC camera can expose (those a given device supports are in [CameraControls.supported]). */
enum class ControlId(
    internal val target: Target,
    internal val selector: Int,
    /** Position of this control in the unit's `bmControls` bitmap. */
    internal val bit: Int,
    /** Size in bytes of the control transfer. */
    internal val size: Int,
    internal val kind: Kind,
    internal val signed: Boolean = false,
    /** Byte offset and size of this control's field when it shares a transfer with another (pan/tilt). */
    internal val offset: Int = 0,
    internal val fieldSize: Int = size,
) {
    // Camera terminal
    /** Auto-exposure mode, a bit flag: see [AutoExposureMode]. */
    AUTO_EXPOSURE_MODE(Target.CAMERA, 0x02, 1, 1, Kind.MODE),

    /** Absolute exposure time in units of 100 µs. */
    EXPOSURE_TIME(Target.CAMERA, 0x04, 3, 4, Kind.RANGE),
    FOCUS(Target.CAMERA, 0x06, 5, 2, Kind.RANGE),
    AUTO_FOCUS(Target.CAMERA, 0x08, 15, 1, Kind.SWITCH),
    IRIS(Target.CAMERA, 0x09, 7, 2, Kind.RANGE),
    ZOOM(Target.CAMERA, 0x0B, 9, 2, Kind.RANGE),

    /** Pan in arc seconds. Shares one transfer with [TILT]. */
    PAN(Target.CAMERA, 0x0D, 11, 8, Kind.RANGE, signed = true, offset = 0, fieldSize = 4),

    /** Tilt in arc seconds. Shares one transfer with [PAN]. */
    TILT(Target.CAMERA, 0x0D, 11, 8, Kind.RANGE, signed = true, offset = 4, fieldSize = 4),

    // Processing unit
    BACKLIGHT_COMPENSATION(Target.PROCESSING, 0x01, 8, 2, Kind.RANGE),
    BRIGHTNESS(Target.PROCESSING, 0x02, 0, 2, Kind.RANGE, signed = true),
    CONTRAST(Target.PROCESSING, 0x03, 1, 2, Kind.RANGE),
    GAIN(Target.PROCESSING, 0x04, 9, 2, Kind.RANGE),

    /** 0 = disabled, 1 = 50 Hz, 2 = 60 Hz. */
    POWER_LINE_FREQUENCY(Target.PROCESSING, 0x05, 10, 1, Kind.RANGE),
    HUE(Target.PROCESSING, 0x06, 2, 2, Kind.RANGE, signed = true),
    SATURATION(Target.PROCESSING, 0x07, 3, 2, Kind.RANGE),
    SHARPNESS(Target.PROCESSING, 0x08, 4, 2, Kind.RANGE),
    GAMMA(Target.PROCESSING, 0x09, 5, 2, Kind.RANGE),

    /** White balance temperature in Kelvin. */
    WHITE_BALANCE_TEMPERATURE(Target.PROCESSING, 0x0A, 6, 2, Kind.RANGE),
    AUTO_WHITE_BALANCE(Target.PROCESSING, 0x0B, 12, 1, Kind.SWITCH);

    internal enum class Target { CAMERA, PROCESSING }

    internal enum class Kind {
        /** A number between the device's minimum and maximum. */
        RANGE,

        /** Off (0) or on (1). */
        SWITCH,

        /** One of a set of bit-flag values reported by the device. */
        MODE,
    }
}

/** Values of [ControlId.AUTO_EXPOSURE_MODE]. A device supports a subset, see [Control.options]. */
object AutoExposureMode {
    const val MANUAL = 1
    const val AUTO = 2
    const val SHUTTER_PRIORITY = 4
    const val APERTURE_PRIORITY = 8
}

/** Minimum, maximum, step and default of a ranged control, as reported by the device. */
data class ControlRange(val min: Int, val max: Int, val step: Int, val default: Int) {
    operator fun contains(value: Int) = value in min..max
}

/** Transport for control transfers, so the logic here can be tested without a device. */
internal interface ControlBackend {
    fun get(unit: Int, selector: Int, request: Int, length: Int): ByteArray
    fun set(unit: Int, selector: Int, data: ByteArray)
}

/**
 * One control of a camera. Values are the raw UVC values (see the [ControlId] docs for units).
 * Calls can throw [ControlException] and can be made before or during streaming.
 */
class Control internal constructor(
    val id: ControlId,
    private val unit: Int,
    private val backend: ControlBackend,
) {
    /**
     * The minimum, maximum, step and default values, or null for a mode control ([ControlId.AUTO_EXPOSURE_MODE]),
     * which has [options] instead. Switches report 0..1.
     */
    val range: ControlRange? by lazy {
        when (id.kind) {
            ControlId.Kind.MODE -> null
            ControlId.Kind.SWITCH -> ControlRange(0, 1, 1, readRequest(GET_DEF))
            ControlId.Kind.RANGE -> ControlRange(
                min = readRequest(GET_MIN),
                max = readRequest(GET_MAX),
                step = readRequest(GET_RES).coerceAtLeast(1),
                default = readRequest(GET_DEF),
            )
        }
    }

    /** The values a mode control accepts (for example [AutoExposureMode] flags), null for other kinds. */
    val options: Set<Int>? by lazy {
        if (id.kind != ControlId.Kind.MODE) {
            null
        } else {
            val bits = readRequest(GET_RES)
            (0 until Int.SIZE_BITS).map { 1 shl it }.filter { bits and it != 0 }.toSet()
        }
    }

    /** The current value (GET_CUR). */
    val value: Int get() = readRequest(GET_CUR)

    /**
     * Writes [newValue] (SET_CUR).
     *
     * @throws IllegalArgumentException if the value is outside [range] (or not one of [options]); the device
     * rounds values that are not a multiple of the range step.
     * @throws ControlException if the device rejects the transfer.
     */
    fun set(newValue: Int) {
        when (id.kind) {
            ControlId.Kind.MODE -> {
                val allowed = options.orEmpty()
                require(newValue in allowed) { "${id.name} must be one of $allowed, was $newValue" }
            }
            else -> {
                val allowed = checkNotNull(range)
                require(newValue in allowed) { "${id.name} must be in ${allowed.min}..${allowed.max}, was $newValue" }
            }
        }
        write(newValue)
    }

    /** Restores the device default (GET_DEF). */
    fun reset() = write(readRequest(GET_DEF))

    private fun readRequest(request: Int): Int =
        decode(backend.get(unit, id.selector, request, id.size), id)

    private fun write(newValue: Int) {
        val data = if (id.fieldSize == id.size) {
            ByteArray(id.size)
        } else {
            // Pan and tilt share one transfer: keep the other field as it is.
            backend.get(unit, id.selector, GET_CUR, id.size)
        }
        encode(newValue, id, data)
        backend.set(unit, id.selector, data)
    }

    internal companion object {
        const val SET_CUR = 0x01
        const val GET_CUR = 0x81
        const val GET_MIN = 0x82
        const val GET_MAX = 0x83
        const val GET_RES = 0x84
        const val GET_DEF = 0x87

        /** Reads [id]'s field from a little-endian control transfer. */
        fun decode(data: ByteArray, id: ControlId): Int {
            check(data.size >= id.offset + id.fieldSize) { "Short ${id.name} transfer: ${data.size} bytes" }
            var result = 0L
            for (i in id.fieldSize - 1 downTo 0) result = (result shl 8) or (data[id.offset + i].toLong() and 0xFF)
            if (id.signed) {
                val shift = 64 - 8 * id.fieldSize
                result = (result shl shift) shr shift
            }
            return result.toInt()
        }

        /** Writes [value] into [id]'s field of a little-endian control transfer. */
        fun encode(value: Int, id: ControlId, into: ByteArray) {
            for (i in 0 until id.fieldSize) into[id.offset + i] = (value shr (8 * i)).toByte()
        }
    }
}

/**
 * The controls of one camera, built from what the device reports it supports: [supported] never
 * contains a control the camera lacks, and [get] returns null for it instead of failing at call time.
 */
class CameraControls internal constructor(backend: ControlBackend, info: IntArray) {
    private val controls: Map<ControlId, Control>

    init {
        val cameraUnit = info[0]
        val cameraBits = info[1]
        val processingUnit = info[2]
        val processingBits = info[3]
        controls = ControlId.values().mapNotNull { id ->
            val (unit, bits) = when (id.target) {
                ControlId.Target.CAMERA -> cameraUnit to cameraBits
                ControlId.Target.PROCESSING -> processingUnit to processingBits
            }
            if (unit != 0 && bits and (1 shl id.bit) != 0) id to Control(id, unit, backend) else null
        }.toMap()
    }

    /** The controls this camera supports. */
    val supported: Set<ControlId> get() = controls.keys

    operator fun get(id: ControlId): Control? = controls[id]

    fun isSupported(id: ControlId) = id in controls

    val autoExposureMode: Control? get() = this[ControlId.AUTO_EXPOSURE_MODE]
    val exposureTime: Control? get() = this[ControlId.EXPOSURE_TIME]
    val focus: Control? get() = this[ControlId.FOCUS]
    val autoFocus: Control? get() = this[ControlId.AUTO_FOCUS]
    val zoom: Control? get() = this[ControlId.ZOOM]
    val pan: Control? get() = this[ControlId.PAN]
    val tilt: Control? get() = this[ControlId.TILT]
    val brightness: Control? get() = this[ControlId.BRIGHTNESS]
    val contrast: Control? get() = this[ControlId.CONTRAST]
    val saturation: Control? get() = this[ControlId.SATURATION]
    val sharpness: Control? get() = this[ControlId.SHARPNESS]
    val gain: Control? get() = this[ControlId.GAIN]
    val whiteBalanceTemperature: Control? get() = this[ControlId.WHITE_BALANCE_TEMPERATURE]
    val autoWhiteBalance: Control? get() = this[ControlId.AUTO_WHITE_BALANCE]
}
