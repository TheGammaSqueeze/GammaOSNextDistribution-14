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

#include <SocketWriterNode.h>
#include <ImsMediaTrace.h>
#include <ImsMediaTimer.h>

SocketWriterNode::SocketWriterNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mSocket = nullptr;
    mSocketOpened = false;
    mDisableSocket = false;
}

SocketWriterNode::~SocketWriterNode()
{
    if (mSocket != nullptr)
    {
        IMLOGE0("[~SocketWriterNode] socket is not closed");
    }
}

kBaseNodeId SocketWriterNode::GetNodeId()
{
    return kNodeIdSocketWriter;
}

ImsMediaResult SocketWriterNode::Start()
{
    IMLOGD1("[Start] media[%d]", mMediaType);
    mSocket = ISocket::GetInstance(mLocalAddress.port, mPeerAddress.ipAddress, mPeerAddress.port);

    if (mSocket == nullptr)
    {
        IMLOGE0("[Start] can't create socket instance");
        return RESULT_NOT_READY;
    }

    // set local/peer address here
    mSocket->SetLocalEndpoint(mLocalAddress.ipAddress, mLocalAddress.port);
    mSocket->SetPeerEndpoint(mPeerAddress.ipAddress, mPeerAddress.port);

    if (!mSocket->Open(mLocalFd))
    {
        IMLOGE0("[Start] can't open socket");
        mSocketOpened = false;
        return RESULT_PORT_UNAVAILABLE;
    }

    mSocket->SetSocketOpt(kSocketOptionIpTos, mDscp);
    mSocketOpened = true;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void SocketWriterNode::Stop()
{
    IMLOGD1("[Stop] media[%d]", mMediaType);

    if (mSocket != nullptr)
    {
        if (mSocketOpened)
        {
            mSocket->Close();
            mSocketOpened = false;
        }

        ISocket::ReleaseInstance(mSocket);
        mSocket = nullptr;
    }

    mNodeState = kNodeStateStopped;
}

bool SocketWriterNode::IsRunTime()
{
    return true;
}

bool SocketWriterNode::IsSourceNode()
{
    return true;
}

void SocketWriterNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    RtpConfig* pConfig = reinterpret_cast<RtpConfig*>(config);

    if (mProtocolType == kProtocolRtp)
    {
        mPeerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());
    }
    else if (mProtocolType == kProtocolRtcp)
    {
        mPeerAddress =
                RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort() + 1);
    }

    mDscp = pConfig->getDscp();
}

bool SocketWriterNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    RtpConfig* pConfig = reinterpret_cast<RtpConfig*>(config);
    RtpAddress peerAddress;

    if (mProtocolType == kProtocolRtp)
    {
        peerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());
    }
    else if (mProtocolType == kProtocolRtcp)
    {
        peerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort() + 1);
    }

    return (mPeerAddress == peerAddress && mDscp == pConfig->getDscp());
}

void SocketWriterNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimestamp, bool /*bMark*/, uint32_t nSeqNum,
        ImsMediaSubType /*nDataType*/, uint32_t arrivalTime)
{
    if (mDisableSocket == true && subtype != MEDIASUBTYPE_RTCPPACKET_BYE)
    {
        IMLOGW3("[OnDataFromFrontNode] media[%d] subtype[%d] socket is disabled, bytes[%d]",
                mMediaType, subtype, nDataSize);
    }

    IMLOGD_PACKET4(IM_PACKET_LOG_SOCKET,
            "[OnDataFromFrontNode] TS[%d], SeqNum[%u], size[%u], timeDiff[%d]", nTimestamp, nSeqNum,
            nDataSize, arrivalTime != 0 ? ImsMediaTimer::GetTimeInMilliSeconds() - arrivalTime : 0);

    if (mSocket == nullptr)
    {
        return;
    }

    mSocket->SendTo(pData, nDataSize);
}

void SocketWriterNode::SetLocalFd(int fd)
{
    mLocalFd = fd;
}

void SocketWriterNode::SetLocalAddress(const RtpAddress& address)
{
    mLocalAddress = address;
}

void SocketWriterNode::SetPeerAddress(const RtpAddress& address)
{
    mPeerAddress = address;
}