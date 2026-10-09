#pragma once

#include <cstddef>
#include <cstdint>

namespace droiduvc {

/**
 * Copies a tightly packed RGB888 image into an RGBX8888 buffer, setting alpha to 0xFF.
 *
 * Both buffers may have row padding (strides are in bytes). Only the overlapping
 * min(width) x min(height) region is copied; the rest of `dst` is left untouched.
 */
void blitRgbToRgbx(const uint8_t *src, size_t srcStrideBytes, uint32_t srcWidth, uint32_t srcHeight,
                   uint8_t *dst, size_t dstStrideBytes, uint32_t dstWidth, uint32_t dstHeight);

}  // namespace droiduvc
