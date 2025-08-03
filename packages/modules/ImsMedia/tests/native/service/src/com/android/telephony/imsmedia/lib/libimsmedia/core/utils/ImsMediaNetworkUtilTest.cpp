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

#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <ImsMediaNetworkUtil.h>
#include <gtest/gtest.h>
#include <ImsMediaTrace.h>

TEST(ImsMediaNetworkUtilTest, GetIPPortFromSocketFDUsingLoopBackIPAddress)
{
    const char szTestIP[] = "127.0.0.1";
    unsigned int nTestPort = 12340;
    int nTestSocFD = ImsMediaNetworkUtil::openSocket(szTestIP, nTestPort, AF_INET);
    ASSERT_NE(nTestSocFD, -1);

    char localIPAddr[32] = {'\0'};
    unsigned int localPort;
    bool res =
            ImsMediaNetworkUtil::getLocalIpPortFromSocket(nTestSocFD, localIPAddr, 32, localPort);

    ASSERT_EQ(res, true);
    ASSERT_EQ(strncmp(localIPAddr, szTestIP, strlen(szTestIP)), 0);
    ASSERT_EQ(localPort, nTestPort);

    res = ImsMediaNetworkUtil::connectSocket(nTestSocFD, szTestIP, nTestPort, AF_INET);
    ASSERT_EQ(res, true);

    char peerIPAddr[32] = {'\0'};
    unsigned int peerPort;
    res = ImsMediaNetworkUtil::getRemoteIpPortFromSocket(nTestSocFD, peerIPAddr, 32, peerPort);

    ASSERT_EQ(res, true);
    ASSERT_EQ(strncmp(peerIPAddr, szTestIP, strlen(szTestIP)), 0);
    ASSERT_EQ(peerPort, nTestPort);

    ImsMediaNetworkUtil::closeSocket(nTestSocFD);

    res = ImsMediaNetworkUtil::connectSocket(nTestSocFD, szTestIP, nTestPort, AF_INET);
    ASSERT_EQ(res, false);
    res = ImsMediaNetworkUtil::getLocalIpPortFromSocket(nTestSocFD, localIPAddr, 32, localPort);
    ASSERT_EQ(res, false);
    res = ImsMediaNetworkUtil::getRemoteIpPortFromSocket(nTestSocFD, peerIPAddr, 32, peerPort);
    ASSERT_EQ(res, false);
}

TEST(ImsMediaNetworkUtilTest, GetIPPortFromSocketFDUsingIpv6Address)
{
    const char szTestIP[] = "::1";
    unsigned int nTestPort = 56780;
    int nTestSocFD = ImsMediaNetworkUtil::openSocket(szTestIP, nTestPort, AF_INET6);
    ASSERT_NE(nTestSocFD, -1);

    char localIPAddr[32] = {'\0'};
    unsigned int localPort;
    bool res =
            ImsMediaNetworkUtil::getLocalIpPortFromSocket(nTestSocFD, localIPAddr, 32, localPort);

    ASSERT_EQ(res, true);
    ASSERT_EQ(strncmp(localIPAddr, szTestIP, strlen(szTestIP)), 0);
    ASSERT_EQ(localPort, nTestPort);

    res = ImsMediaNetworkUtil::connectSocket(nTestSocFD, szTestIP, nTestPort, AF_INET6);
    ASSERT_EQ(res, true);

    char peerIPAddr[32] = {'\0'};
    unsigned int peerPort;
    res = ImsMediaNetworkUtil::getRemoteIpPortFromSocket(nTestSocFD, peerIPAddr, 32, peerPort);

    ASSERT_EQ(res, true);
    ASSERT_EQ(strncmp(peerIPAddr, szTestIP, strlen(szTestIP)), 0);
    ASSERT_EQ(peerPort, nTestPort);

    ImsMediaNetworkUtil::closeSocket(nTestSocFD);

    res = ImsMediaNetworkUtil::connectSocket(nTestSocFD, szTestIP, nTestPort, AF_INET6);
    ASSERT_EQ(res, false);
    res = ImsMediaNetworkUtil::getLocalIpPortFromSocket(nTestSocFD, localIPAddr, 32, localPort);
    ASSERT_EQ(res, false);
    res = ImsMediaNetworkUtil::getRemoteIpPortFromSocket(nTestSocFD, peerIPAddr, 32, peerPort);
    ASSERT_EQ(res, false);
}