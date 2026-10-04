#include <gtest/gtest.h>

#include "stream_mode.h"

using polaroid::FrameSize;
using polaroid::orderBySimilarity;
using polaroid::intervalToFps;

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

TEST(IntervalToFps, CommonFrameRates) {
    EXPECT_EQ(intervalToFps(333333), 30);
    EXPECT_EQ(intervalToFps(666666), 15);
    EXPECT_EQ(intervalToFps(400000), 25);
    EXPECT_EQ(intervalToFps(166666), 60);
}

TEST(IntervalToFps, RoundsToNearest) {
    EXPECT_EQ(intervalToFps(344827), 29);  // 29.0000...
    EXPECT_EQ(intervalToFps(416666), 24);  // 24.0000...
    EXPECT_EQ(intervalToFps(1000000), 10);
}

TEST(IntervalToFps, ZeroIsInvalid) {
    EXPECT_EQ(intervalToFps(0), 0);
}

TEST(PixelFormatCode, MatchesKotlinStreamFormat) {
    // Keep in sync with StreamFormat in UvcCamera.kt (pinned there by UvcCameraTest).
    EXPECT_EQ(polaroid::kAnyFormat, -1);
    EXPECT_EQ(polaroid::kMjpeg, 0);
    EXPECT_EQ(polaroid::kYuyv, 1);
    EXPECT_EQ(polaroid::kOtherFormat, 2);
}
