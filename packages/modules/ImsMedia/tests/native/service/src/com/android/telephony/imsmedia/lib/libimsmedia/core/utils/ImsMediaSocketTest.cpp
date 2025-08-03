/**
 * Copyright (C) 2023 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <ISocket.h>
#include <ImsMediaNetworkUtil.h>

class ImsMediaSocketTest : public ::testing::Test
{
public:
    ISocket* mSocket = nullptr;
    int mSocketRtpFd = -1;

protected:
    virtual void SetUp() override
    {
        const char peerIpAddress[] = "127.0.0.1";
        const char localIpAddress[] = "10.67.75.151";
        uint32_t peerPort = 12340;
        uint32_t localPort = 50080;

        mSocket = ISocket::GetInstance(localPort, peerIpAddress, peerPort);
        mSocketRtpFd = ImsMediaNetworkUtil::openSocket(peerIpAddress, peerPort, AF_INET);
        EXPECT_NE(mSocketRtpFd, -1);

        mSocket->SetLocalEndpoint(localIpAddress, localPort);
        mSocket->SetPeerEndpoint(peerIpAddress, peerPort);
        EXPECT_EQ(mSocket->Open(mSocketRtpFd), true);
    }

    virtual void TearDown() override
    {
        if (mSocketRtpFd != -1)
        {
            ImsMediaNetworkUtil::closeSocket(mSocketRtpFd);
        }
        ISocket::ReleaseInstance(mSocket);
    }
};

TEST_F(ImsMediaSocketTest, dscpTest)
{
    EXPECT_EQ(mSocket->SetSocketOpt(kSocketOptionIpTos, -72), true);
    EXPECT_EQ(mSocket->SetSocketOpt(kSocketOptionIpTos, 0), true);
    EXPECT_EQ(mSocket->SetSocketOpt(kSocketOptionIpTos, 4), true);
    EXPECT_EQ(mSocket->SetSocketOpt(kSocketOptionIpTos, 46), true);
}

TEST_F(ImsMediaSocketTest, invalidSocketFdTest)
{
    ImsMediaNetworkUtil::closeSocket(mSocketRtpFd);
    mSocketRtpFd = -1;
    EXPECT_EQ(mSocket->SetSocketOpt(kSocketOptionIpTos, 46), false);
}

TEST_F(ImsMediaSocketTest, invalidSocketOptionTest)
{
    EXPECT_EQ(mSocket->SetSocketOpt(kSocketOptionNone, 34), false);
}
