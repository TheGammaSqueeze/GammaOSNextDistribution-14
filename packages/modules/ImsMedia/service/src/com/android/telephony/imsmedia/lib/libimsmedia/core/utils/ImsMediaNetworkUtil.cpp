/**
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

#include <errno.h>
#include <arpa/inet.h>
#include <ImsMediaNetworkUtil.h>
#include <ImsMediaTrace.h>

#define V4MAPPED_OFFSET   12
#define NUM_OF_BYTES_IPV4 4
#define NUM_OF_BYTES_IPV6 6

static bool GetIpPortFromSockAddr(
        const sockaddr_storage& ss, char* ipAddress, int len, unsigned int& port)
{
    const sockaddr_in6& sin6 = reinterpret_cast<const sockaddr_in6&>(ss);
    if (ss.ss_family == AF_INET6 && IN6_IS_ADDR_V4MAPPED(&sin6.sin6_addr))
    {
        // Copy the IPv6 address into the temporary sockaddr_storage.
        sockaddr_storage tmp;
        memset(&tmp, 0, sizeof(tmp));
        memcpy(&tmp, &ss, sizeof(sockaddr_in6));
        // Unmap it into an IPv4 address.
        sockaddr_in& sin = reinterpret_cast<sockaddr_in&>(tmp);
        sin.sin_family = AF_INET;
        sin.sin_port = sin6.sin6_port;
        memcpy(&sin.sin_addr.s_addr, &sin6.sin6_addr.s6_addr[V4MAPPED_OFFSET], NUM_OF_BYTES_IPV4);
        // Do the regular conversion using the unmapped address.
        return GetIpPortFromSockAddr(tmp, ipAddress, len, port);
    }

    if (ss.ss_family == AF_INET)
    {
        const sockaddr_in& sin = reinterpret_cast<const sockaddr_in&>(ss);
        strncpy(ipAddress, inet_ntoa(sin.sin_addr), len);
        // memcpy(ipAddress, &(sin.sin_addr.s_addr), sizeof(struct in_addr));
        port = ntohs(sin.sin_port);
    }
    else if (ss.ss_family == AF_INET6)
    {
        inet_ntop(AF_INET6, sin6.sin6_addr.s6_addr, ipAddress, len);
        port = ntohs(sin6.sin6_port);
    }
    else
    {
        return false;
    }

    IMLOGD2("[GetIpPortFromSockAddr] %s:%u", ipAddress, port);
    return true;
}

bool ImsMediaNetworkUtil::getLocalIpPortFromSocket(
        const int nSocketFD, char* pIPAddress, int len, unsigned int& port)
{
    if (pIPAddress == nullptr)
    {
        return false;
    }

    sockaddr_storage ss;
    sockaddr* sa = reinterpret_cast<sockaddr*>(&ss);
    socklen_t byteCount = sizeof(ss);
    errno = 0;
    int res = getsockname(nSocketFD, sa, &byteCount);
    if (res == -1)
    {
        IMLOGE1("[getLocalIpPortFromSocket] getsockname failed. Error[%d]", errno);
        return false;
    }

    return GetIpPortFromSockAddr(ss, pIPAddress, len, port);
}

bool ImsMediaNetworkUtil::getRemoteIpPortFromSocket(
        const int nSocketFD, char* pIPAddress, int len, unsigned int& port)
{
    if (pIPAddress == nullptr)
    {
        return false;
    }

    sockaddr_storage ss;
    sockaddr* sa = reinterpret_cast<sockaddr*>(&ss);
    socklen_t byteCount = sizeof(ss);
    errno = 0;
    int res = getpeername(nSocketFD, sa, &byteCount);
    if (res == -1)
    {
        IMLOGE1("[getRemoteIpPortFromSocket] getpeername failed. Error[%d]", errno);
        return false;
    }

    return GetIpPortFromSockAddr(ss, pIPAddress, len, port);
}

int ImsMediaNetworkUtil::openSocket(const char* pIPAddr, unsigned int port, int af)
{
    int soc = 0;
    if ((soc = socket(af, SOCK_DGRAM, IPPROTO_UDP)) < 0)
    {
        IMLOGE1("[openSocket] error[%d]", errno);
        return -1;
    }

    if (af == AF_INET)
    {
        sockaddr_in sin;
        sin.sin_family = AF_INET;
        sin.sin_port = htons(port);

        if (inet_pton(AF_INET, pIPAddr, &sin.sin_addr) <= 0)
        {
            IMLOGE1("[openSocket] inet_pton error[%d]", errno);
            return -1;
        }

        if (bind(soc, (struct sockaddr*)&sin, sizeof(sin)) < 0)
        {
            IMLOGE1("[openSocket] bind error[%d]", errno);
            return -1;
        }
    }
    else if (af == AF_INET6)
    {
        sockaddr_in6 sin6;
        sin6.sin6_family = AF_INET6;
        sin6.sin6_port = htons(port);

        if (inet_pton(AF_INET6, pIPAddr, &sin6.sin6_addr) <= 0)
        {
            IMLOGE1("[openSocket] error[%d]", errno);
            return -1;
        }

        if (bind(soc, (struct sockaddr*)&sin6, sizeof(sin6)) < 0)
        {
            IMLOGE1("[openSocket] bind error[%d]", errno);
            return -1;
        }
    }

    return soc;
}

bool ImsMediaNetworkUtil::connectSocket(
        const int socketFd, const char* pIPAddr, unsigned int port, int af)
{
    if (socketFd == -1)
    {
        IMLOGE0("[connectSocket] invalid socket fd");
        return false;
    }

    if (af == AF_INET)
    {
        sockaddr_in sin;
        sin.sin_family = AF_INET;
        sin.sin_port = htons(port);

        if (inet_pton(AF_INET, pIPAddr, &sin.sin_addr) <= 0)
        {
            IMLOGE1("[connectSocket] inet_pton error[%d]", errno);
            return -1;
        }

        if (connect(socketFd, (struct sockaddr*)&sin, sizeof(sockaddr_in)) < 0)
        {
            IMLOGE1("[connectSocket] connect error[%d]", errno);
            return false;
        }
    }
    else if (af == AF_INET6)
    {
        sockaddr_in6 sin6;
        sin6.sin6_family = AF_INET6;
        sin6.sin6_port = htons(port);

        if (inet_pton(AF_INET6, pIPAddr, &sin6.sin6_addr) <= 0)
        {
            IMLOGE1("[connectSocket] error[%d]", errno);
            return -1;
        }

        if (connect(socketFd, (struct sockaddr*)&sin6, sizeof(sockaddr_in6)) < 0)
        {
            IMLOGE1("[connectSocket] error[%d]", errno);
            return false;
        }
    }

    return true;
}

void ImsMediaNetworkUtil::closeSocket(int& socketFd)
{
    shutdown(socketFd, SHUT_RDWR);
    close(socketFd);
    socketFd = -1;
}