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

#include <VideoStreamGraphRtcp.h>
#include <RtcpEncoderNode.h>
#include <RtcpDecoderNode.h>
#include <SocketReaderNode.h>
#include <SocketWriterNode.h>
#include <ImsMediaNetworkUtil.h>
#include <ImsMediaTrace.h>
#include <VideoConfig.h>

VideoStreamGraphRtcp::VideoStreamGraphRtcp(BaseSessionCallback* callback, int localFd) :
        VideoStreamGraph(callback, localFd)
{
}

VideoStreamGraphRtcp::~VideoStreamGraphRtcp() {}

ImsMediaResult VideoStreamGraphRtcp::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mConfig = new VideoConfig(reinterpret_cast<VideoConfig*>(config));
    BaseNode* pNodeRtcpEncoder = new RtcpEncoderNode(mCallback);
    pNodeRtcpEncoder->SetMediaType(IMS_MEDIA_VIDEO);

    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort - 1);
    (static_cast<RtcpEncoderNode*>(pNodeRtcpEncoder))->SetLocalAddress(localAddress);
    pNodeRtcpEncoder->SetConfig(config);
    AddNode(pNodeRtcpEncoder);

    BaseNode* pNodeSocketWriter = new SocketWriterNode(mCallback);
    pNodeSocketWriter->SetMediaType(IMS_MEDIA_VIDEO);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalFd(mLocalFd);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))
            ->SetLocalAddress(RtpAddress(localIp, localPort));
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetProtocolType(kProtocolRtcp);
    pNodeSocketWriter->SetConfig(config);
    AddNode(pNodeSocketWriter);
    pNodeRtcpEncoder->ConnectRearNode(pNodeSocketWriter);

    BaseNode* pNodeSocketReader = new SocketReaderNode(mCallback);
    pNodeSocketReader->SetMediaType(IMS_MEDIA_VIDEO);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetLocalFd(mLocalFd);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))
            ->SetLocalAddress(RtpAddress(localIp, localPort));
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetProtocolType(kProtocolRtcp);
    pNodeSocketReader->SetConfig(config);
    AddNode(pNodeSocketReader);

    BaseNode* pNodeRtcpDecoder = new RtcpDecoderNode(mCallback);
    pNodeRtcpDecoder->SetMediaType(IMS_MEDIA_VIDEO);
    (static_cast<RtcpDecoderNode*>(pNodeRtcpDecoder))->SetLocalAddress(localAddress);
    pNodeRtcpDecoder->SetConfig(config);
    AddNode(pNodeRtcpDecoder);
    pNodeSocketReader->ConnectRearNode(pNodeRtcpDecoder);

    setState(StreamState::kStreamStateCreated);
    return ImsMediaResult::RESULT_SUCCESS;
}

ImsMediaResult VideoStreamGraphRtcp::update(RtpConfig* config)
{
    IMLOGI1("[update] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);

    if (*reinterpret_cast<VideoConfig*>(mConfig) == *pConfig)
    {
        IMLOGI0("[update] no update");
        return RESULT_SUCCESS;
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
    }

    mConfig = new VideoConfig(pConfig);

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

ImsMediaResult VideoStreamGraphRtcp::start()
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

bool VideoStreamGraphRtcp::setMediaQualityThreshold(MediaQualityThreshold* threshold)
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

bool VideoStreamGraphRtcp::OnEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    IMLOGI3("[OnEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);

    bool ret = false;

    switch (type)
    {
        case kRequestVideoSendNack:
        case kRequestVideoSendPictureLost:
        case kRequestVideoSendTmmbr:
        case kRequestVideoSendTmmbn:
        {
            BaseNode* node = findNode(kNodeIdRtcpEncoder);
            InternalRequestEventParam* param = reinterpret_cast<InternalRequestEventParam*>(param1);

            if (node != nullptr && param != nullptr)
            {
                RtcpEncoderNode* encoder = reinterpret_cast<RtcpEncoderNode*>(node);

                if (type == kRequestVideoSendNack)
                {
                    ret = encoder->SendNack(&param->nackParams);
                }
                else if (type == kRequestVideoSendPictureLost)
                {
                    ret = encoder->SendPictureLost(param->value);
                }
                else if (type == kRequestVideoSendTmmbr || type == kRequestVideoSendTmmbn)
                {
                    ret = encoder->SendTmmbrn(param->type, &param->tmmbrParams);
                }

                delete param;
            }
        }
        break;
    }

    return ret;
}