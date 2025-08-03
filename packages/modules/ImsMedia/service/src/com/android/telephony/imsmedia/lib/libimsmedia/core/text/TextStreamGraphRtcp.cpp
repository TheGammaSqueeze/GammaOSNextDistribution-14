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

#include <TextStreamGraphRtcp.h>
#include <RtcpEncoderNode.h>
#include <RtcpDecoderNode.h>
#include <SocketReaderNode.h>
#include <SocketWriterNode.h>
#include <ImsMediaNetworkUtil.h>
#include <ImsMediaTrace.h>
#include <TextConfig.h>

TextStreamGraphRtcp::TextStreamGraphRtcp(BaseSessionCallback* callback, int localFd) :
        TextStreamGraph(callback, localFd)
{
}

TextStreamGraphRtcp::~TextStreamGraphRtcp() {}

ImsMediaResult TextStreamGraphRtcp::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mConfig = new TextConfig(reinterpret_cast<TextConfig*>(config));
    BaseNode* pNodeRtcpEncoder = new RtcpEncoderNode(mCallback);
    pNodeRtcpEncoder->SetMediaType(IMS_MEDIA_TEXT);

    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort - 1);
    (static_cast<RtcpEncoderNode*>(pNodeRtcpEncoder))->SetLocalAddress(localAddress);
    pNodeRtcpEncoder->SetConfig(config);
    AddNode(pNodeRtcpEncoder);

    BaseNode* pNodeSocketWriter = new SocketWriterNode(mCallback);
    pNodeSocketWriter->SetMediaType(IMS_MEDIA_TEXT);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalFd(mLocalFd);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))
            ->SetLocalAddress(RtpAddress(localIp, localPort));
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetProtocolType(kProtocolRtcp);
    pNodeSocketWriter->SetConfig(config);
    AddNode(pNodeSocketWriter);
    pNodeRtcpEncoder->ConnectRearNode(pNodeSocketWriter);

    BaseNode* pNodeSocketReader = new SocketReaderNode(mCallback);
    pNodeSocketReader->SetMediaType(IMS_MEDIA_TEXT);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetLocalFd(mLocalFd);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))
            ->SetLocalAddress(RtpAddress(localIp, localPort));
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetProtocolType(kProtocolRtcp);
    pNodeSocketReader->SetConfig(config);
    AddNode(pNodeSocketReader);

    BaseNode* pNodeRtcpDecoder = new RtcpDecoderNode(mCallback);
    pNodeRtcpDecoder->SetMediaType(IMS_MEDIA_TEXT);
    (static_cast<RtcpDecoderNode*>(pNodeRtcpDecoder))->SetLocalAddress(localAddress);
    pNodeRtcpDecoder->SetConfig(config);
    AddNode(pNodeRtcpDecoder);
    pNodeSocketReader->ConnectRearNode(pNodeRtcpDecoder);

    setState(StreamState::kStreamStateCreated);
    return ImsMediaResult::RESULT_SUCCESS;
}

ImsMediaResult TextStreamGraphRtcp::update(RtpConfig* config)
{
    IMLOGI1("[update] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    if (*mConfig == *pConfig)
    {
        IMLOGI0("[update] no update");
        return RESULT_SUCCESS;
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
    }

    mConfig = new TextConfig(pConfig);

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW)
    {
        IMLOGI0("[update] pause RTCP");
        return stop();
    }

    ImsMediaResult ret = ImsMediaResult::RESULT_NOT_READY;
    // stop scheduler
    if (mGraphState == kStreamStateRunning)
    {
        mScheduler->Stop();
    }

    for (auto& node : mListNodeStarted)
    {
        if (node != nullptr)
        {
            IMLOGD1("[update] update node[%s]", node->GetNodeName());
            ret = node->UpdateConfig(pConfig);

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE2("[update] error in update node[%s], ret[%d]", node->GetNodeName(), ret);
            }
        }
    }

    if (mGraphState == kStreamStateCreated &&
            mConfig->getMediaDirection() != RtpConfig::MEDIA_DIRECTION_NO_FLOW)
    {
        IMLOGI0("[update] resume RTCP");
        return start();
    }

    // restart scheduler
    if (mGraphState == kStreamStateRunning)
    {
        mScheduler->Start();
    }

    return ret;
}

ImsMediaResult TextStreamGraphRtcp::start()
{
    IMLOGI1("[start] state[%d]", mGraphState);

    if (mConfig == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    if (mConfig->getMediaDirection() != RtpConfig::MEDIA_DIRECTION_NO_FLOW)
    {
        return BaseStreamGraph::start();
    }

    // not started
    return RESULT_SUCCESS;
}

bool TextStreamGraphRtcp::setMediaQualityThreshold(MediaQualityThreshold* threshold)
{
    if (threshold != nullptr)
    {
        BaseNode* node = findNode(kNodeIdRtcpDecoder);

        if (node != nullptr)
        {
            RtcpDecoderNode* decoder = reinterpret_cast<RtcpDecoderNode*>(node);
            decoder->SetInactivityTimerSec(threshold->getRtcpInactivityTimerMillis() / 1000);
            return true;
        }
    }

    return false;
}