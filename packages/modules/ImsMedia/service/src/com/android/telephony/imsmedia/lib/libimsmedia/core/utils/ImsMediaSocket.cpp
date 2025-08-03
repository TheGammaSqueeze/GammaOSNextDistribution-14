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

#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>
#include <errno.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <thread>
#include <ImsMediaSocket.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <IImsMediaThread.h>

// static valuable
std::list<ImsMediaSocket*> ImsMediaSocket::slistRxSocket;
std::list<ImsMediaSocket*> ImsMediaSocket::slistSocket;
int32_t ImsMediaSocket::sRxSocketCount = 0;
bool ImsMediaSocket::mSocketListUpdated = false;
bool ImsMediaSocket::mTerminateMonitor = false;
ImsMediaCondition ImsMediaSocket::mConditionExit;
std::mutex ImsMediaSocket::sMutexRxSocket;
std::mutex ImsMediaSocket::sMutexSocketList;

enum kDscp
{
    DSCP_CS0 = 0,
    DSCP_CS1 = 8,
    DSCP_CS2 = 16,
    DSCP_CS3 = 24,
    DSCP_CS4 = 32,
    DSCP_CS5 = 40,
    DSCP_CS6 = 48,
    DSCP_CS7 = 56,
    DSCP_AF11 = 10,
    DSCP_AF12 = 12,
    DSCP_AF13 = 14,
    DSCP_AF21 = 18,
    DSCP_AF22 = 20,
    DSCP_AF23 = 22,
    DSCP_AF31 = 26,
    DSCP_AF32 = 28,
    DSCP_AF33 = 30,
    DSCP_AF41 = 34,
    DSCP_AF42 = 36,
    DSCP_AF43 = 38,
    DSCP_EF = 46,
    DSCP_VOICEADMIT = 44,
};

const std::initializer_list<kDscp> dscpValues = {DSCP_EF, DSCP_AF41, DSCP_CS0, DSCP_CS1, DSCP_CS2,
        DSCP_CS3, DSCP_CS4, DSCP_CS5, DSCP_CS6, DSCP_CS7, DSCP_AF11, DSCP_AF12, DSCP_AF13,
        DSCP_AF21, DSCP_AF22, DSCP_AF23, DSCP_AF31, DSCP_AF32, DSCP_AF33, DSCP_AF42, DSCP_AF43,
        DSCP_VOICEADMIT};

ImsMediaSocket* ImsMediaSocket::GetInstance(
        uint32_t localPort, const char* peerIpAddress, uint32_t peerPort)
{
    ImsMediaSocket* pImsMediaSocket = nullptr;
    std::lock_guard<std::mutex> guard(sMutexSocketList);

    for (auto& i : slistSocket)
    {
        if (strcmp(i->GetPeerIPAddress(), peerIpAddress) == 0 && i->GetLocalPort() == localPort &&
                i->GetPeerPort() == peerPort)
        {
            return i;
        }
    }

    pImsMediaSocket = new ImsMediaSocket();
    return pImsMediaSocket;
}

void ImsMediaSocket::ReleaseInstance(ImsMediaSocket* pSocket)
{
    if (pSocket != nullptr && pSocket->mRefCount == 0)
    {
        delete pSocket;
    }
}

ImsMediaSocket::ImsMediaSocket()
{
    mListener = nullptr;
    mRefCount = 0;
    mLocalIPVersion = IPV4;
    mPeerIPVersion = IPV4;
    mLocalPort = 0;
    mPeerPort = 0;
    mSocketFd = -1;
    mRemoteIpFiltering = true;
    IMLOGD0("[ImsMediaSocket] enter");
}

ImsMediaSocket::~ImsMediaSocket()
{
    IMLOGD_PACKET5(IM_PACKET_LOG_SOCKET, "[~ImsMediaSocket] %x, %s:%d %s:%d", this, mLocalIP,
            mLocalPort, mPeerIP, mPeerPort);
}

void ImsMediaSocket::SetLocalEndpoint(const char* ipAddress, const uint32_t port)
{
    strlcpy(mLocalIP, ipAddress, MAX_IP_LEN);
    mLocalPort = port;

    if (strstr(mLocalIP, ":") == nullptr)
    {
        mLocalIPVersion = IPV4;
    }
    else
    {
        mLocalIPVersion = IPV6;
    }
}

void ImsMediaSocket::SetPeerEndpoint(const char* ipAddress, const uint32_t port)
{
    strlcpy(mPeerIP, ipAddress, MAX_IP_LEN);
    mPeerPort = port;

    if (strstr(mPeerIP, ":") == nullptr)
    {
        mPeerIPVersion = IPV4;
    }
    else
    {
        mPeerIPVersion = IPV6;
    }
}

int ImsMediaSocket::GetLocalPort()
{
    return mLocalPort;
}
int ImsMediaSocket::GetPeerPort()
{
    return mPeerPort;
}
char* ImsMediaSocket::GetLocalIPAddress()
{
    return mLocalIP;
}
char* ImsMediaSocket::GetPeerIPAddress()
{
    return mPeerIP;
}

bool ImsMediaSocket::Open(int socketFd)
{
    if (socketFd == -1)
    {
        return false;
    }

    IMLOGD5("[Open] %s:%d, %s:%d, nRefCount[%d]", mLocalIP, mLocalPort, mPeerIP, mPeerPort,
            mRefCount);

    if (mRefCount > 0)
    {
        IMLOGD0("[Open] exit - Socket is opened already");
        mRefCount++;
        return true;
    }

    mSocketFd = socketFd;
    sMutexSocketList.lock();
    slistSocket.push_back(this);
    mRefCount++;
    sMutexSocketList.unlock();
    return true;
}

void ImsMediaSocket::Listen(ISocketListener* listener)
{
    IMLOGD0("[Listen]");
    mListener = listener;

    if (listener != nullptr)
    {
        // add socket list, run thread
        sMutexRxSocket.lock();
        slistRxSocket.push_back(this);
        sMutexRxSocket.unlock();

        if (sRxSocketCount == 0)
        {
            StartSocketMonitor();
        }
        else
        {
            mSocketListUpdated = true;
        }

        sRxSocketCount++;
        IMLOGD1("[Listen] add sRxSocketCount[%d]", sRxSocketCount);
    }
    else
    {
        sMutexRxSocket.lock();
        slistRxSocket.remove(this);
        sMutexRxSocket.unlock();
        sRxSocketCount--;

        if (sRxSocketCount <= 0)
        {
            StopSocketMonitor();
            sRxSocketCount = 0;
        }
        else
        {
            mSocketListUpdated = true;
        }

        IMLOGD1("[Listen] remove RxSocketCount[%d]", sRxSocketCount);
    }
}

int32_t ImsMediaSocket::SendTo(uint8_t* pData, uint32_t nDataSize)
{
    int32_t len;
    IMLOGD_PACKET2(IM_PACKET_LOG_SOCKET, "[SendTo] fd[%d],[%d] bytes", mSocketFd, nDataSize);

    if (nDataSize == 0)
    {
        return 0;
    }

    struct sockaddr_in stAddr4;
    struct sockaddr_in6 stAddr6;
    struct sockaddr* pstSockAddr = nullptr;
    socklen_t nSockAddrLen = 0;

    if (mPeerIPVersion == IPV4)
    {
        nSockAddrLen = sizeof(stAddr4);
        memset(&stAddr4, 0, nSockAddrLen);
        stAddr4.sin_family = AF_INET;
        stAddr4.sin_port = htons(mPeerPort);

        if (inet_pton(AF_INET, mPeerIP, &(stAddr4.sin_addr.s_addr)) != 1)
        {
            IMLOGE1("[ImsMediaSocket:SendTo] IPv4[%s]", mPeerIP);
            return 0;
        }

        pstSockAddr = (struct sockaddr*)&stAddr4;
    }
    else
    {
        nSockAddrLen = sizeof(stAddr6);
        memset(&stAddr6, 0, nSockAddrLen);
        stAddr6.sin6_family = AF_INET6;
        stAddr6.sin6_port = htons(mPeerPort);

        if (inet_pton(AF_INET6, mPeerIP, &(stAddr6.sin6_addr.s6_addr)) != 1)
        {
            IMLOGE1("[ImsMediaSocket:SendTo] Ipv6[%s]", mPeerIP);
            return 0;
        }

        pstSockAddr = (struct sockaddr*)&stAddr6;
    }

    len = sendto(mSocketFd, reinterpret_cast<const char*>(pData), nDataSize, 0, pstSockAddr,
            nSockAddrLen);

    if (len < 0)
    {
        IMLOGE4("[ImsMediaSocket:SendTo] FAILED len(%d), nDataSize(%d) failed (%d, %s)", len,
                nDataSize, errno, strerror(errno));
    }

    return len;
}

int32_t ImsMediaSocket::ReceiveFrom(uint8_t* pData, uint32_t nBufferSize)
{
    int32_t len;
    struct sockaddr* pstSockAddr = nullptr;
    socklen_t nSockAddrLen = 0;
    sockaddr_storage ss;
    pstSockAddr = reinterpret_cast<sockaddr*>(&ss);
    len = recvfrom(mSocketFd, pData, nBufferSize, 0, pstSockAddr, &nSockAddrLen);

    if (len > 0)
    {
        static char pSourceIP[MAX_IP_LEN];
        memset(pSourceIP, 0, sizeof(pSourceIP));
        IMLOGD_PACKET2(IM_PACKET_LOG_SOCKET, "[ReceiveFrom] fd[%d], len[%d]", mSocketFd, len);
    }
    else if (EWOULDBLOCK == errno)
    {
        IMLOGE0("[ReceiveFrom], WBlock");
    }
    else
    {
        IMLOGE0("[ReceiveFrom] Fail");
    }

    return len;
}

bool ImsMediaSocket::RetrieveOptionMsg(uint32_t type, int32_t& value)
{
    if (type == kSocketOptionIpTtl)
    {
        uint8_t buffer[DEFAULT_MTU];
        struct iovec iov[1] = {{buffer, sizeof(buffer)}};
        struct sockaddr_storage srcAddress;
        uint8_t ctrlDataBuffer[CMSG_SPACE(1) + CMSG_SPACE(1) + CMSG_SPACE(1)];
        struct msghdr hdr = {.msg_name = &srcAddress,
                .msg_namelen = sizeof(srcAddress),
                .msg_iov = iov,
                .msg_iovlen = 1,
                .msg_control = ctrlDataBuffer,
                .msg_controllen = sizeof(ctrlDataBuffer)};

        if (recvmsg(mSocketFd, &hdr, 0) > 0)
        {
            struct cmsghdr* cmsg = CMSG_FIRSTHDR(&hdr);

            for (; cmsg; cmsg = CMSG_NXTHDR(&hdr, cmsg))
            {
                if (cmsg->cmsg_level == IPPROTO_IP && cmsg->cmsg_type == IP_RECVTTL)
                {
                    uint8_t* ttlPtr = reinterpret_cast<uint8_t*>(CMSG_DATA(cmsg));
                    value = reinterpret_cast<int32_t&>(*ttlPtr);
                    return true;
                }
            }
        }
        else
        {
            IMLOGE1("[RetrieveOptionMsg] fail to read type[%d]", type);
        }
    }

    return false;
}

void ImsMediaSocket::Close()
{
    IMLOGD1("[Close] enter, nRefCount[%d]", mRefCount);
    mRefCount--;

    if (mRefCount > 0)
    {
        IMLOGD0("[Close] exit - Socket is used");
        return;
    }

    // close(mSocketFd);
    std::lock_guard<std::mutex> guard(sMutexSocketList);
    slistSocket.remove(this);
    IMLOGD0("[Close] exit");
}

bool ImsMediaSocket::isValidDscp(int32_t dscp)
{
    for (const auto value : dscpValues)
    {
        if (value == dscp)
        {
            return true;
        }
    }

    return false;
}

int32_t ImsMediaSocket::convertDscpToTos(int32_t dscp)
{
    if (dscp == DSCP_CS0 || !isValidDscp(dscp))
    {
        return (DSCP_EF << 2) & 0XFF;
    }

    return (dscp << 2) & 0xFF;
}

bool ImsMediaSocket::SetSocketOpt(kSocketOption nOption, int32_t nOptionValue)
{
    if (mSocketFd == -1)
    {
        IMLOGD0("[SetSocketOpt] socket handle is invalid");
        return false;
    }

    int32_t tos = 0;

    switch (nOption)
    {
        case kSocketOptionIpTos:
            tos = convertDscpToTos(nOptionValue);
            if (mLocalIPVersion == IPV4)
            {
                if (-1 == setsockopt(mSocketFd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos)))
                {
                    IMLOGW0("[SetSocketOpt] IP_TOS - IPv4");
                    return false;
                }
            }
            else
            {
                if (-1 == setsockopt(mSocketFd, IPPROTO_IPV6, IPV6_TCLASS, &tos, sizeof(tos)))
                {
                    IMLOGW0("[SetSocketOpt] IP_TOS - IPv6");
                    return false;
                }
            }

            IMLOGD1("[SetSocketOpt] IP_QOS[%d]", tos);
            break;
        case kSocketOptionIpTtl:
            if (-1 ==
                    setsockopt(
                            mSocketFd, IPPROTO_IP, IP_RECVTTL, &nOptionValue, sizeof(nOptionValue)))
            {
                IMLOGW0("[SetSocketOpt] IP_RECVTTL");
                return false;
            }
            IMLOGD0("[SetSocketOpt] IP_RECVTTL");
            return true;
        default:
            IMLOGD1("[SetSocketOpt] Unsupported socket option[%d]", nOption);
            return false;
    }

    return true;
}

int32_t ImsMediaSocket::GetSocketFd()
{
    return mSocketFd;
}

ISocketListener* ImsMediaSocket::GetListener()
{
    return mListener;
}

void ImsMediaSocket::StartSocketMonitor()
{
    if (mTerminateMonitor == true)
    {
        IMLOGD0("[StartSocketMonitor] Send Signal");
        mTerminateMonitor = false;
        mConditionExit.signal();
        return;
    }

    mTerminateMonitor = false;
    IMLOGD_PACKET0(IM_PACKET_LOG_SOCKET, "[StartSocketMonitor] start monitor thread");

    std::thread socketMonitorThread(&ImsMediaSocket::SocketMonitorThread);
    socketMonitorThread.detach();
}

void ImsMediaSocket::StopSocketMonitor()
{
    IMLOGD_PACKET0(IM_PACKET_LOG_SOCKET, "[StopSocketMonitor] stop monitor thread");
    mTerminateMonitor = true;
    mConditionExit.wait();
}

uint32_t ImsMediaSocket::SetSocketFD(void* pReadFds, void* pWriteFds, void* pExceptFds)
{
    uint32_t nMaxSD = 0;
    std::lock_guard<std::mutex> guard(sMutexRxSocket);
    FD_ZERO(reinterpret_cast<fd_set*>(pReadFds));
    FD_ZERO(reinterpret_cast<fd_set*>(pWriteFds));
    FD_ZERO(reinterpret_cast<fd_set*>(pExceptFds));
    IMLOGD_PACKET0(IM_PACKET_LOG_SOCKET, "[SetSocketFD]");

    for (auto& i : slistRxSocket)
    {
        int32_t socketFD = i->GetSocketFd();
        FD_SET(socketFD, reinterpret_cast<fd_set*>(pReadFds));

        if (socketFD > nMaxSD)
        {
            nMaxSD = socketFD;
        }
    }

    mSocketListUpdated = false;
    return nMaxSD;
}

void ImsMediaSocket::ReadDataFromSocket(void* pReadfds)
{
    std::lock_guard<std::mutex> guard(sMutexRxSocket);
    IMLOGD_PACKET0(IM_PACKET_LOG_SOCKET, "[ReadDataFromSocket]");

    for (auto& rxSocket : slistRxSocket)
    {
        if (rxSocket != nullptr)
        {
            int32_t socketFD = rxSocket->GetSocketFd();

            if (FD_ISSET(socketFD, reinterpret_cast<fd_set*>(pReadfds)))
            {
                IMLOGD_PACKET1(IM_PACKET_LOG_SOCKET,
                        "[ReadDataFromSocket] send notify to listener %p", rxSocket->GetListener());

                if (rxSocket->GetListener() != nullptr)
                {
                    rxSocket->GetListener()->OnReadDataFromSocket();
                }
            }
        }
    }
}

void ImsMediaSocket::SocketMonitorThread()
{
    static fd_set ReadFds;
    static fd_set WriteFds;
    static fd_set ExceptFds;
    static fd_set TmpReadfds;
    static fd_set TmpWritefds;
    static fd_set TmpExcepfds;
    int nMaxSD;
    IMLOGD0("[SocketMonitorThread] enter");
    IImsMediaThread::SetThreadPriority(getpid(), gettid(), THREAD_PRIORITY_REALTIME);
    nMaxSD = SetSocketFD(&ReadFds, &WriteFds, &ExceptFds);

    for (;;)
    {
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 100 * 1000;  // micro-second

        if (mTerminateMonitor)
        {
            break;
        }

        if (mSocketListUpdated)
        {
            nMaxSD = SetSocketFD(&ReadFds, &WriteFds, &ExceptFds);
        }

        memcpy(&TmpReadfds, &ReadFds, sizeof(fd_set));
        memcpy(&TmpWritefds, &WriteFds, sizeof(fd_set));
        memcpy(&TmpExcepfds, &ExceptFds, sizeof(fd_set));

        int32_t res = select(nMaxSD + 1, &TmpReadfds, &TmpWritefds, &TmpExcepfds, &tv);

        if (mTerminateMonitor)
        {
            break;
        }

        if (res == -1)
        {
            IMLOGE0("[SocketMonitorThread] select function Error!!");
        }
        else
        {
            ReadDataFromSocket(&TmpReadfds);
        }
    }

    IMLOGD0("[SocketMonitorThread] exit");
    mTerminateMonitor = false;
    mConditionExit.signal();
}
