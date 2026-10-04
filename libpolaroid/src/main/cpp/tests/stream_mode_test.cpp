#include <gtest/gtest.h>

#include "stream_mode.h"

using polaroid::FrameSize;
using polaroid::orderBySimilarity;
using polaroid::packSize;

TEST(OrderBySimilarity, ExactMatchComesFirst) {
    auto result = orderBySimilarity({{1920, 1080}, {640, 480}, {1280, 720}}, 640, 480);
    ASSERT_EQ(result.size(), 3u);
    EXPECT_EQ(result[0], (FrameSize{640, 480}));
    EXPECT_EQ(result[1], (FrameSize{1280, 720}));
    EXPECT_EQ(result[2], (FrameSize{1920, 1080}));
}

TEST(OrderBySimilarity, PicksClosestWhenThereIsNoExactMatch) {
    auto result = orderBySimilarity({{1920, 1080}, {1280, 720}, {320, 240}}, 640, 480);
    EXPECT_EQ(result.front(), (FrameSize{320, 240}));  // distance 560 vs 880
}

TEST(OrderBySimilarity, TiesKeepOriginalOrder) {
    auto result = orderBySimilarity({{800, 480}, {480, 480}}, 640, 480);  // both 160 away
    EXPECT_EQ(result[0], (FrameSize{800, 480}));
    EXPECT_EQ(result[1], (FrameSize{480, 480}));
}

TEST(OrderBySimilarity, DropsDuplicatesFromMultipleFormats) {
    // The same size is typically advertised by both MJPEG and YUYV.
    auto result = orderBySimilarity({{640, 480}, {1280, 720}, {640, 480}}, 640, 480);
    EXPECT_EQ(result.size(), 2u);
}

TEST(OrderBySimilarity, EmptyInputGivesEmptyOutput) {
    EXPECT_TRUE(orderBySimilarity({}, 640, 480).empty());
}

TEST(PackSize, MatchesTheKotlinDecoding) {
    EXPECT_EQ(packSize(1280, 720), (int64_t{1280} << 32) | 720);
    EXPECT_EQ(packSize(0, 0), 0);
    const int64_t packed = packSize(1920, 1080);
    EXPECT_EQ(static_cast<int>(packed >> 32), 1920);
    EXPECT_EQ(static_cast<int>(packed & 0xFFFFFFFF), 1080);
}
