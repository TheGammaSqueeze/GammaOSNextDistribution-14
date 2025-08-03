#include <gtest/gtest.h>
#include <chrono>
#include <thread>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <limits.h>

class ImsMediaTimerTest : public ::testing::Test
{
public:
    bool mCallbackCalled = false;
    hTimerHandler mTimer = nullptr;

    virtual void SetUp() override
    {
        mCallbackCalled = false;
        mTimer = nullptr;
    }

    virtual void TearDown() override {}

    static void TimerCallback(hTimerHandler, void* data)
    {
        ImsMediaTimerTest* pImsMediaTimerTest = (ImsMediaTimerTest*)data;
        pImsMediaTimerTest->mCallbackCalled = true;
    }
};

TEST_F(ImsMediaTimerTest, StopTimerBeforeExpiry1)
{
    hTimerHandler hTimer = ImsMediaTimer::TimerStart(1, false, TimerCallback, this);
    EXPECT_EQ(ImsMediaTimer::TimerStop(hTimer, nullptr), true);
    EXPECT_EQ(mCallbackCalled, false);
}

TEST_F(ImsMediaTimerTest, StopTimerBeforeExpiry2)
{
    hTimerHandler hTimer = ImsMediaTimer::TimerStart(10, false, TimerCallback, this);
    EXPECT_EQ(ImsMediaTimer::TimerStop(hTimer, nullptr), true);
    EXPECT_EQ(mCallbackCalled, false);
}

TEST_F(ImsMediaTimerTest, StopTimerBeforeExpiry3)
{
    hTimerHandler hTimer = ImsMediaTimer::TimerStart(100, false, TimerCallback, this);
    EXPECT_EQ(ImsMediaTimer::TimerStop(hTimer, nullptr), true);
    EXPECT_EQ(mCallbackCalled, false);
}

TEST_F(ImsMediaTimerTest, StopTimerBeforeExpiry4)
{
    hTimerHandler hTimer = ImsMediaTimer::TimerStart(1000, false, TimerCallback, this);
    EXPECT_EQ(ImsMediaTimer::TimerStop(hTimer, nullptr), true);
    EXPECT_EQ(mCallbackCalled, false);
}

TEST_F(ImsMediaTimerTest, StopTimerAfterExpiry)
{
    hTimerHandler hTimer = ImsMediaTimer::TimerStart(200, false, TimerCallback, this);
    std::this_thread::sleep_for(std::chrono::milliseconds(300));
    EXPECT_EQ(ImsMediaTimer::TimerStop(hTimer, nullptr), false);
    EXPECT_EQ(mCallbackCalled, true);
}

TEST_F(ImsMediaTimerTest, MicrosecTimeDifference)
{
    const uint32_t timeDiff = 100000;
    uint64_t timeStart = ImsMediaTimer::GetTimeInMicroSeconds();
    std::this_thread::sleep_for(std::chrono::microseconds(timeDiff));
    uint64_t timeEnd = ImsMediaTimer::GetTimeInMicroSeconds();
    EXPECT_TRUE(timeEnd - timeStart >= timeDiff);
}

TEST_F(ImsMediaTimerTest, MillisecTimeDifference)
{
    const uint32_t timeDiff = 100;
    uint32_t timeStart = ImsMediaTimer::GetTimeInMilliSeconds();
    std::this_thread::sleep_for(std::chrono::milliseconds(timeDiff));
    uint32_t timeEnd = ImsMediaTimer::GetTimeInMilliSeconds();
    EXPECT_TRUE(timeEnd - timeStart >= timeDiff);
}

TEST_F(ImsMediaTimerTest, CheckMillisecTimeOverflow)
{
    const uint32_t timeDiff = 100;
    ImsMediaTimer::SetStartTimeInMicroSeconds(static_cast<uint64_t>(UINT_MAX) * 1000);
    const uint32_t timeStart = ImsMediaTimer::GetTimeInMilliSeconds();
    std::this_thread::sleep_for(std::chrono::milliseconds(timeDiff));
    const uint32_t timeEnd = ImsMediaTimer::GetTimeInMilliSeconds();

    EXPECT_TRUE(timeEnd - timeStart <= timeDiff * 1.05f);
}