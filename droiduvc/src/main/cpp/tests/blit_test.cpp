#include <gtest/gtest.h>

#include <vector>

#include "blit.h"

using droiduvc::blitRgbToRgbx;

TEST(Blit, ConvertsPixelsAndSetsOpaqueAlpha) {
    const uint8_t src[] = {1, 2, 3, 4, 5, 6};  // two pixels
    std::vector<uint8_t> dst(8, 0);
    blitRgbToRgbx(src, 6, 2, 1, dst.data(), 8, 2, 1);
    EXPECT_EQ(dst, (std::vector<uint8_t>{1, 2, 3, 0xFF, 4, 5, 6, 0xFF}));
}

TEST(Blit, HonoursDestinationStridePadding) {
    // 2x2 source, destination rows are 16 bytes (4 pixels) wide.
    const uint8_t src[] = {1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4};
    std::vector<uint8_t> dst(32, 0xAA);
    blitRgbToRgbx(src, 6, 2, 2, dst.data(), 16, 4, 2);
    EXPECT_EQ(dst[0], 1);
    EXPECT_EQ(dst[4], 2);
    EXPECT_EQ(dst[16], 3);
    EXPECT_EQ(dst[20], 4);
    // Padding beyond the copied pixels is untouched.
    EXPECT_EQ(dst[8], 0xAA);
    EXPECT_EQ(dst[24], 0xAA);
}

TEST(Blit, HonoursSourceStridePadding) {
    // 1x2 source with 4 bytes per row (1 padding byte).
    const uint8_t src[] = {9, 8, 7, 0, 6, 5, 4, 0};
    std::vector<uint8_t> dst(8, 0);
    blitRgbToRgbx(src, 4, 1, 2, dst.data(), 4, 1, 2);
    EXPECT_EQ(dst, (std::vector<uint8_t>{9, 8, 7, 0xFF, 6, 5, 4, 0xFF}));
}

TEST(Blit, ClipsToTheSmallerBuffer) {
    const uint8_t src[] = {1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4};  // 2x2
    std::vector<uint8_t> dst(4, 0);                              // 1x1
    blitRgbToRgbx(src, 6, 2, 2, dst.data(), 4, 1, 1);
    EXPECT_EQ(dst, (std::vector<uint8_t>{1, 1, 1, 0xFF}));
}

TEST(Blit, EmptyImageWritesNothing) {
    std::vector<uint8_t> dst(4, 0x55);
    blitRgbToRgbx(nullptr, 0, 0, 0, dst.data(), 4, 1, 1);
    EXPECT_EQ(dst, (std::vector<uint8_t>(4, 0x55)));
}
