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

int intervalToFps(uint32_t interval100ns) {
    if (interval100ns == 0) return 0;
    return static_cast<int>((10000000.0 / interval100ns) + 0.5);
}

}  // namespace polaroid
