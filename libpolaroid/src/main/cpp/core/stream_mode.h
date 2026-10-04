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

/** Packs a size as `(width << 32) | height`, the value `NativeUvc.start` returns to Kotlin. */
int64_t packSize(int width, int height);

}  // namespace polaroid
