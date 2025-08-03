#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "VkQsriTimeline.h"

namespace gfxstream {
namespace vk {
namespace {
using ::testing::InSequence;
using ::testing::MockFunction;

TEST(VkQsriTImelineTest, signalFirstRegisterCallbackLater) {
    MockFunction<void()> mockCallback1, mockCallback2;
    VkQsriTimeline qsriTimeline;
    {
        InSequence s;
        EXPECT_CALL(mockCallback1, Call()).Times(1);
        EXPECT_CALL(mockCallback2, Call()).Times(1);
    }
    qsriTimeline.signalNextPresentAndPoll();
    qsriTimeline.signalNextPresentAndPoll();
    qsriTimeline.registerCallbackForNextPresentAndPoll(mockCallback1.AsStdFunction());
    qsriTimeline.registerCallbackForNextPresentAndPoll(mockCallback2.AsStdFunction());
}

TEST(VkQsriTImelineTest, registerCallbackFirstSignalLater) {
    MockFunction<void()> mockCallback1, mockCallback2, beforeSignal;
    VkQsriTimeline qsriTimeline;
    {
        InSequence s;
        EXPECT_CALL(beforeSignal, Call()).Times(1);
        EXPECT_CALL(mockCallback1, Call()).Times(1);
        EXPECT_CALL(mockCallback2, Call()).Times(1);
    }
    qsriTimeline.registerCallbackForNextPresentAndPoll(mockCallback1.AsStdFunction());
    qsriTimeline.registerCallbackForNextPresentAndPoll(mockCallback2.AsStdFunction());
    beforeSignal.Call();
    qsriTimeline.signalNextPresentAndPoll();
    qsriTimeline.signalNextPresentAndPoll();
}

}  // namespace
}  // namespace vk
}  // namespace gfxstream