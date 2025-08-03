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

#include <VideoStreamGraphRtpRx.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <VideoConfig.h>
#include <RtpDecoderNode.h>
#include <SocketReaderNode.h>
#include <VideoRtpPayloadDecoderNode.h>
#include <IVideoRendererNode.h>

VideoStreamGraphRtpRx::VideoStreamGraphRtpRx(BaseSessionCallback* callback, int localFd) :
        VideoStreamGraph(callback, localFd)
{
    mSurface = nullptr;
}

VideoStreamGraphRtpRx::~VideoStreamGraphRtpRx() {}

ImsMediaResult VideoStreamGraphRtpRx::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mConfig = new VideoConfig(reinterpret_cast<VideoConfig*>(config));

    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort);

    BaseNode* nodeSocketReader = new SocketReaderNode(mCallback);
    nodeSocketReader->SetMediaType(IMS_MEDIA_VIDEO);
    (static_cast<SocketReaderNode*>(nodeSocketReader))->SetLocalFd(mLocalFd);
    (static_cast<SocketReaderNode*>(nodeSocketReader))->SetLocalAddress(localAddress);
    (static_cast<SocketReaderNode*>(nodeSocketReader))->SetProtocolType(kProtocolRtp);
    nodeSocketReader->SetConfig(config);
    AddNode(nodeSocketReader);

    BaseNode* nodeRtpDecoder = new RtpDecoderNode(mCallback);
    nodeRtpDecoder->SetMediaType(IMS_MEDIA_VIDEO);
    nodeRtpDecoder->SetConfig(mConfig);
    (static_cast<RtpDecoderNode*>(nodeRtpDecoder))->SetLocalAddress(localAddress);
    AddNode(nodeRtpDecoder);
    nodeSocketReader->ConnectRearNode(nodeRtpDecoder);

    BaseNode* rtpPayloadDecoder = new VideoRtpPayloadDecoderNode(mCallback);
    rtpPayloadDecoder->SetMediaType(IMS_MEDIA_VIDEO);
    rtpPayloadDecoder->SetConfig(mConfig);
    rtpPayloadDecoder->SetSchedulerCallback(
            std::static_pointer_cast<StreamSchedulerCallback>(mScheduler));
    AddNode(rtpPayloadDecoder);
    nodeRtpDecoder->ConnectRearNode(rtpPayloadDecoder);

    BaseNode* renderer = new IVideoRendererNode(mCallback);
    renderer->SetMediaType(IMS_MEDIA_VIDEO);
    renderer->SetConfig(mConfig);
    renderer->SetSchedulerCallback(std::static_pointer_cast<StreamSchedulerCallback>(mScheduler));
    AddNode(renderer);
    rtpPayloadDecoder->ConnectRearNode(renderer);
    setState(StreamState::kStreamStateCreated);
    return RESULT_SUCCESS;
}

ImsMediaResult VideoStreamGraphRtpRx::update(RtpConfig* config)
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

    if (mGraphState == kStreamStateWaitSurface)
    {
        setState(StreamState::kStreamStateCreated);
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
        mConfig = nullptr;
    }

    mConfig = new VideoConfig(pConfig);

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI0("[update] pause RX");
        return stop();
    }

    ImsMediaResult ret = RESULT_NOT_READY;

    if (mGraphState == kStreamStateRunning)
    {
        mScheduler->Stop();

        for (auto& node : mListNodeStarted)
        {
            if (node != nullptr)
            {
                IMLOGD1("[update] update node[%s]", node->GetNodeName());
                ret = node->UpdateConfig(mConfig);
                if (ret != RESULT_SUCCESS)
                {
                    IMLOGE2("[update] error in update node[%s], ret[%d]", node->GetNodeName(), ret);
                }
            }
        }

        mScheduler->Start();
    }
    else if (mGraphState == kStreamStateCreated)
    {
        for (auto& node : mListNodeToStart)
        {
            if (node != nullptr)
            {
                IMLOGD1("[update] update node[%s]", node->GetNodeName());
                ret = node->UpdateConfig(mConfig);

                if (ret != RESULT_SUCCESS)
                {
                    IMLOGE2("[update] error in update node[%s], ret[%d]", node->GetNodeName(), ret);
                }
            }
        }
    }

    if (mGraphState == kStreamStateCreated &&
            (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
                    pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE))
    {
        IMLOGI0("[update] resume RX");
        return start();
    }

    return ret;
}

ImsMediaResult VideoStreamGraphRtpRx::start()
{
    IMLOGI1("[start] state[%d]", mGraphState);

    if (mConfig == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(mConfig);

    if (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
            pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI1("[start] direction[%d] no need to start", pConfig->getMediaDirection());
        return RESULT_SUCCESS;
    }

    if (mSurface == nullptr)
    {
        IMLOGI2("[start] direction[%d], mode[%d], surface is not ready, wait",
                pConfig->getMediaDirection(), pConfig->getVideoMode());
        setState(StreamState::kStreamStateWaitSurface);
        return RESULT_SUCCESS;
    }

    ImsMediaResult result = startNodes();

    if (result != RESULT_SUCCESS)
    {
        setState(StreamState::kStreamStateCreated);
        mCallback->SendEvent(kImsMediaEventNotifyError, result, kStreamModeRtpRx);
        return result;
    }

    setState(StreamState::kStreamStateRunning);
    return RESULT_SUCCESS;
}

bool VideoStreamGraphRtpRx::setMediaQualityThreshold(MediaQualityThreshold* threshold)
{
    if (threshold != nullptr)
    {
        BaseNode* node = findNode(kNodeIdRtpDecoder);

        if (node != nullptr)
        {
            RtpDecoderNode* decoder = reinterpret_cast<RtpDecoderNode*>(node);
            decoder->SetInactivityTimerSec(threshold->getRtpInactivityTimerMillis().empty()
                            ? 0
                            : threshold->getRtpInactivityTimerMillis().front() / 1000);
            return true;
        }

        node = findNode(kNodeIdVideoRenderer);

        if (node != nullptr)
        {
            IVideoRendererNode* decoder = reinterpret_cast<IVideoRendererNode*>(node);
            decoder->SetPacketLossParam(threshold->getRtpPacketLossDurationMillis(),
                    threshold->getRtpPacketLossRate().empty()
                            ? 0
                            : threshold->getRtpPacketLossRate().front());
            return true;
        }
    }

    return false;
}

void VideoStreamGraphRtpRx::setSurface(ANativeWindow* surface)
{
    IMLOGD0("[setSurface]");

    if (surface != nullptr)
    {
        mSurface = surface;

        BaseNode* node = findNode(kNodeIdVideoRenderer);

        if (node != nullptr)
        {
            IVideoRendererNode* renderer = reinterpret_cast<IVideoRendererNode*>(node);
            renderer->UpdateSurface(surface);

            if (getState() == StreamState::kStreamStateWaitSurface)
            {
                setState(StreamState::kStreamStateCreated);
                start();
            }
        }
    }
}

bool VideoStreamGraphRtpRx::OnEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    IMLOGI3("[OnEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);

    switch (type)
    {
        case kRequestRoundTripTimeDelayUpdate:
        {
            BaseNode* node = findNode(kNodeIdVideoRenderer);

            if (node != nullptr)
            {
                IVideoRendererNode* pNode = reinterpret_cast<IVideoRendererNode*>(node);
                pNode->UpdateRoundTripTimeDelay(param1);
                return true;
            }
        }
        break;
        default:
            break;
    }

    return false;
}