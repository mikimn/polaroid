#include "stream_mode.h"

#include <algorithm>
#include <cstdlib>

namespace polaroid {

std::vector<FrameSize> orderBySimilarity(const std::vector<FrameSize> &supported, int width, int height) {
    std::vector<FrameSize> unique;
    for (const auto &size : supported) {
        if (std::find(unique.begin(), unique.end(), size) == unique.end()) unique.push_back(size);
    }
    auto distance = [&](const FrameSize &s) { return std::abs(s.width - width) + std::abs(s.height - height); };
    std::stable_sort(unique.begin(), unique.end(), [&](const FrameSize &a, const FrameSize &b) {
        return distance(a) < distance(b);
    });
    return unique;
}

int64_t packSize(int width, int height) {
    return (static_cast<int64_t>(width) << 32) | static_cast<int64_t>(static_cast<uint32_t>(height));
}

}  // namespace polaroid
