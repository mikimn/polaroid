#pragma once

#include <cstdint>
#include <vector>

namespace polaroid {

struct FrameSize {
    int width;
    int height;

    bool operator==(const FrameSize &o) const { return width == o.width && height == o.height; }
};

/**
 * Orders the sizes a camera supports by closeness to the requested size, closest first
 * (distance is |dw| + |dh|). Duplicates are dropped and ties keep their original order, so the
 * camera's own preference wins between equally close sizes.
 */
std::vector<FrameSize> orderBySimilarity(const std::vector<FrameSize> &supported, int width, int height);

/**
 * Frames per second for a UVC frame interval (100 ns units), rounded to the nearest integer.
 * Returns 0 for an invalid (zero) interval.
 */
int intervalToFps(uint32_t interval100ns);

/** Pixel format codes shared with Kotlin's `PixelFormat` (see `UvcNative.kt`). */
enum PixelFormatCode : int { kAnyFormat = -1, kMjpeg = 0, kYuyv = 1, kOtherFormat = 2 };

}  // namespace polaroid
