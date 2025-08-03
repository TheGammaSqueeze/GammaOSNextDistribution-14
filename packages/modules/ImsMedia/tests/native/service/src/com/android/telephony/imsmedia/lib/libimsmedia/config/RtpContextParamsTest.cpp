
#include <RtpContextParams.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

TEST(RtpContextParamsTest, TestDefaultValues)
{
    RtpContextParams rtpContextParams;

    EXPECT_EQ(rtpContextParams.getSsrc(), 0);
    EXPECT_EQ(rtpContextParams.getTimestamp(), 0);
    EXPECT_EQ(rtpContextParams.getSequenceNumber(), 0);
}

TEST(RtpContextParamsTest, TestConstructors)
{
    RtpContextParams rtpContextParams1(100, 200, 300);
    EXPECT_EQ(rtpContextParams1.getSsrc(), 100);
    EXPECT_EQ(rtpContextParams1.getTimestamp(), 200);
    EXPECT_EQ(rtpContextParams1.getSequenceNumber(), 300);

    RtpContextParams rtpContextParams2(rtpContextParams1);
    EXPECT_EQ(rtpContextParams2.getSsrc(), 100);
    EXPECT_EQ(rtpContextParams2.getTimestamp(), 200);
    EXPECT_EQ(rtpContextParams2.getSequenceNumber(), 300);
}

TEST(RtpContextParamsTest, TestOperators)
{
    RtpContextParams rtpContextParams1(10, 20, 30);
    RtpContextParams rtpContextParams2(40, 50, 60);

    EXPECT_EQ(rtpContextParams1 == rtpContextParams2, false);
    EXPECT_EQ(rtpContextParams1 != rtpContextParams2, true);

    rtpContextParams2 = rtpContextParams1;
    EXPECT_EQ(rtpContextParams2.getSsrc(), 10);
    EXPECT_EQ(rtpContextParams2.getTimestamp(), 20);
    EXPECT_EQ(rtpContextParams2.getSequenceNumber(), 30);

    EXPECT_EQ(rtpContextParams1 == rtpContextParams2, true);
    EXPECT_EQ(rtpContextParams1 != rtpContextParams2, false);
}

TEST(RtpContextParamsTest, TestGetterSetters)
{
    RtpContextParams rtpContextParams;

    rtpContextParams.setSsrc(1000);
    rtpContextParams.setTimestamp(2000);
    rtpContextParams.setSequenceNumber(3000);

    EXPECT_EQ(rtpContextParams.getSsrc(), 1000);
    EXPECT_EQ(rtpContextParams.getTimestamp(), 2000);
    EXPECT_EQ(rtpContextParams.getSequenceNumber(), 3000);

    rtpContextParams.setDefaultConfig();
    EXPECT_EQ(rtpContextParams.getSsrc(), 0);
    EXPECT_EQ(rtpContextParams.getTimestamp(), 0);
    EXPECT_EQ(rtpContextParams.getSequenceNumber(), 0);
}