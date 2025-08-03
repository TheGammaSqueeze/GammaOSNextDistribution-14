/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <RtpSession.h>
#include <RtpStack.h>
#include <gtest/gtest.h>

class RtpStackTest : public ::testing::Test
{
public:
    RtpStack rtpStack;
    RtpStackProfile* pobjStackProfile = new RtpStackProfile();

protected:
    virtual void SetUp() override { rtpStack.setStackProfile(pobjStackProfile); }

    virtual void TearDown() override {}
};

TEST_F(RtpStackTest, TestConstructor)
{
    RtpStack rtpStack2;

    // Check default value
    EXPECT_TRUE(rtpStack2.getStackProfile() == nullptr);

    RtpStackProfile* pobjStackProfile3 = new RtpStackProfile();
    RtpStack rtpStack3(pobjStackProfile3);
    EXPECT_TRUE(reinterpret_cast<void*>(rtpStack3.getStackProfile()) ==
            reinterpret_cast<void*>(pobjStackProfile3));
}

TEST_F(RtpStackTest, TestGetSets)
{
    EXPECT_TRUE(reinterpret_cast<void*>(rtpStack.getStackProfile()) ==
            reinterpret_cast<void*>(pobjStackProfile));
    EXPECT_EQ(rtpStack.getStackProfile()->getTermNumber(), pobjStackProfile->getTermNumber());
}

TEST_F(RtpStackTest, TestCreateCheckDeleteRtpSession)
{
    RtpSession* pobjRtpSession = rtpStack.createRtpSession();

    // set ipaddress
    RtpDt_UChar szLocalIP[] = "2600:380:44da:2f25:0:16:649e:b401";
    RtpBuffer pobjTransAddr(
            (RtpDt_UInt32)(strlen(reinterpret_cast<const RtpDt_Char*>(szLocalIP)) + 1),
            reinterpret_cast<RtpDt_UChar*>(szLocalIP));
    pobjRtpSession->setRtpTransAddr(&pobjTransAddr);

    EXPECT_EQ(rtpStack.isValidRtpSession(pobjRtpSession), eRTP_SUCCESS);
    EXPECT_EQ(rtpStack.deleteRtpSession(pobjRtpSession), RTP_SUCCESS);
}

TEST_F(RtpStackTest, TestDeleteRtpSessionFailures)
{
    RtpSession* pobjRtpSession1 = rtpStack.createRtpSession();

    RtpStackProfile* pobjStackProfile2 = new RtpStackProfile();
    RtpStack rtpStack2(pobjStackProfile2);
    RtpSession* pobjRtpSession2 = rtpStack2.createRtpSession();

    // check for RtpSession that doesn't exist in rtpStack
    EXPECT_EQ(rtpStack.deleteRtpSession(pobjRtpSession2), RTP_FAILURE);

    // check for Invalid param
    EXPECT_EQ(rtpStack.deleteRtpSession(nullptr), RTP_INVALID_PARAMS);

    // delete Rtp Sessions
    EXPECT_EQ(rtpStack.deleteRtpSession(pobjRtpSession1), RTP_SUCCESS);
    EXPECT_EQ(rtpStack2.deleteRtpSession(pobjRtpSession2), RTP_SUCCESS);
}