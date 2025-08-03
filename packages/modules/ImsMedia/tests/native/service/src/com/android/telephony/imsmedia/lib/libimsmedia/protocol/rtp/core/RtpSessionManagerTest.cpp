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

#include <RtpSessionManager.h>
#include <RtpSession.h>
#include <gtest/gtest.h>

class RtpSessionManagerTest : public ::testing::Test
{
public:
    RtpSessionManager* pobjActSesDb;
    RtpSession pobjRtpSession1;
    RtpSession pobjRtpSession2;
    eRtp_Bool bResult = eRTP_FALSE;

protected:
    virtual void SetUp() override
    {
        pobjActSesDb = RtpSessionManager::getInstance();
        pobjActSesDb->addRtpSession((RtpDt_Void*)&pobjRtpSession1);
        pobjActSesDb->addRtpSession((RtpDt_Void*)&pobjRtpSession2);
    }

    virtual void TearDown() override
    {
        pobjActSesDb->removeRtpSession((RtpDt_Void*)&pobjRtpSession1);
        pobjActSesDb->removeRtpSession((RtpDt_Void*)&pobjRtpSession2);
    }
};

TEST_F(RtpSessionManagerTest, TestisValidRtpSession)
{
    bResult = pobjActSesDb->isValidRtpSession((RtpDt_Void*)&pobjRtpSession1);
    EXPECT_EQ(bResult, eRTP_TRUE);
    bResult = pobjActSesDb->isValidRtpSession((RtpDt_Void*)&pobjRtpSession2);
    EXPECT_EQ(bResult, eRTP_TRUE);
}

TEST_F(RtpSessionManagerTest, TestisValidRtpSessionwithNonmember)
{
    RtpSession pobjRtpSession3;
    bResult = pobjActSesDb->isValidRtpSession((RtpDt_Void*)&pobjRtpSession3);
    EXPECT_EQ(bResult, eRTP_FALSE);
}

TEST_F(RtpSessionManagerTest, TestisValidRtpSessionwithnull)
{
    RtpSession pobjRtpSession3;
    pobjActSesDb->addRtpSession(nullptr);
    pobjActSesDb->addRtpSession((RtpDt_Void*)&pobjRtpSession3);
    bResult = pobjActSesDb->isValidRtpSession((RtpDt_Void*)&pobjRtpSession3);
    EXPECT_EQ(bResult, eRTP_FALSE);
}

TEST_F(RtpSessionManagerTest, TestremoveRtpSession)
{
    RtpSession pobjRtpSession3;
    pobjActSesDb->addRtpSession((RtpDt_Void*)&pobjRtpSession3);
    pobjActSesDb->removeRtpSession((RtpDt_Void*)&pobjRtpSession3);
    bResult = pobjActSesDb->isValidRtpSession((RtpDt_Void*)&pobjRtpSession3);
    EXPECT_EQ(bResult, eRTP_FALSE);
}