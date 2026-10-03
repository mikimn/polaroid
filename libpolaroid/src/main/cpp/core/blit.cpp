#include "blit.h"

#include <algorithm>

namespace polaroid {

void blitRgbToRgbx(const uint8_t *src, size_t srcStrideBytes, uint32_t srcWidth, uint32_t srcHeight,
                   uint8_t *dst, size_t dstStrideBytes, uint32_t dstWidth, uint32_t dstHeight) {
    const uint32_t rows = std::min(srcHeight, dstHeight);
    const uint32_t cols = std::min(srcWidth, dstWidth);
    for (uint32_t y = 0; y < rows; y++) {
        const uint8_t *s = src + y * srcStrideBytes;
        uint8_t *d = dst + y * dstStrideBytes;
        for (uint32_t x = 0; x < cols; x++, s += 3, d += 4) {
            d[0] = s[0];
            d[1] = s[1];
            d[2] = s[2];
            d[3] = 0xFF;
        }
    }
}

}  // namespace polaroid
