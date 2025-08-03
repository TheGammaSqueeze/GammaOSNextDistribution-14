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

#include <VideoStreamGraphRtpTx.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <VideoConfig.h>
#include <RtpEncoderNode.h>
#include <SocketWriterNode.h>
#include <VideoRtpPayloadEncoderNode.h>
#include <IVideoSourceNode.h>

VideoStreamGraphRtpTx::VideoStreamGraphRtpTx(BaseSessionCallback* callback, int localFd) :
        VideoStreamGraph(callback, localFd)
{
    mSurface = nullptr;
    mVideoMode = -1;
}

VideoStreamGraphRtpTx::~VideoStreamGraphRtpTx() {}

ImsMediaResult VideoStreamGraphRtpTx::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);

    if (pConfig->getVideoMode() == VideoConfig::VIDEO_MODE_PREVIEW)
    {
        return createPreviewMode(pConfig);
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
        mConfig = nullptr;
    }

    mConfig = new VideoConfig(pConfig);

    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort);

    BaseNode* pNodeSource = new IVideoSourceNode(mCallback);
    pNodeSource->SetMediaType(IMS_MEDIA_VIDEO);
    pNodeSource->SetConfig(mConfig);
    AddNode(pNodeSource);

    BaseNode* pNodeRtpPayloadEncoder = new VideoRtpPayloadEncoderNode(mCallback);
    pNodeRtpPayloadEncoder->SetMediaType(IMS_MEDIA_VIDEO);
    pNodeRtpPayloadEncoder->SetConfig(mConfig);
    AddNode(pNodeRtpPayloadEncoder);
    pNodeSource->ConnectRearNode(pNodeRtpPayloadEncoder);

    BaseNode* pNodeRtpEncoder = new RtpEncoderNode(mCallback);
    pNodeRtpEncoder->SetMediaType(IMS_MEDIA_VIDEO);
    pNodeRtpEncoder->SetConfig(mConfig);
    (static_cast<RtpEncoderNode*>(pNodeRtpEncoder))->SetLocalAddress(localAddress);
    AddNode(pNodeRtpEncoder);
    pNodeRtpPayloadEncoder->ConnectRearNode(pNodeRtpEncoder);

    BaseNode* pNodeSocketWriter = new SocketWriterNode(mCallback);
    pNodeSocketWriter->SetMediaType(IMS_MEDIA_VIDEO);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalFd(mLocalFd);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalAddress(localAddress);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetProtocolType(kProtocolRtp);
    pNodeSocketWriter->SetConfig(config);
    AddNode(pNodeSocketWriter);
    pNodeRtpEncoder->ConnectRearNode(pNodeSocketWriter);

    setState(kStreamStateCreated);
    mVideoMode = pConfig->getVideoMode();
    return RESULT_SUCCESS;
}

ImsMediaResult VideoStreamGraphRtpTx::update(RtpConfig* config)
{
    IMLOGI2("[update] current mode[%d], state[%d]", mVideoMode, mGraphState);

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
        setState(kStreamStateCreated);
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
        mConfig = nullptr;
    }

    ImsMediaResult result = RESULT_NOT_READY;

    if (pConfig->getVideoMode() != mVideoMode &&
            (mVideoMode == VideoConfig::VIDEO_MODE_PREVIEW ||
                    pConfig->getVideoMode() == VideoConfig::VIDEO_MODE_PREVIEW))
    {
        result = stop();

        if (result != RESULT_SUCCESS)
        {
            return result;
        }

        /** delete nodes */
        deleteNodes();
        mSurface = nullptr;

        /** create nodes */
        result = create(pConfig);

        if (result != RESULT_SUCCESS)
        {
            return result;
        }

        return start();
    }

    mConfig = new VideoConfig(pConfig);

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI0("[update] pause TX");
        return stop();
    }

    if (pConfig->getVideoMode() != VideoConfig::VIDEO_MODE_PAUSE_IMAGE && mSurface == nullptr)
    {
        IMLOGI2("[update] direction[%d], mode[%d], surface is not ready, wait",
                pConfig->getMediaDirection(), pConfig->getVideoMode());

        if (mGraphState == kStreamStateRunning)
        {
            stop();
        }

        updateNodes(mConfig);
        setState(kStreamStateWaitSurface);
        return RESULT_SUCCESS;
    }

    result = updateNodes(mConfig);

    if (result != RESULT_SUCCESS)
    {
        return result;
    }

    if (mGraphState == kStreamStateCreated &&
            (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
                    pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE))
    {
        IMLOGI0("[update] resume TX");
        return start();
    }

    return result;
}

ImsMediaResult VideoStreamGraphRtpTx::start()
{
    IMLOGI2("[start] current mode[%d], state[%d]", mVideoMode, mGraphState);

    if (mConfig == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(mConfig);

    if (pConfig->getVideoMode() != VideoConfig::VIDEO_MODE_PREVIEW &&
            (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
                    pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
                    pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE))
    {
        IMLOGI1("[start] direction[%d] no need to start", pConfig->getMediaDirection());
        return RESULT_SUCCESS;
    }

    if (pConfig->getVideoMode() != VideoConfig::VIDEO_MODE_PAUSE_IMAGE && mSurface == nullptr)
    {
        IMLOGI2("[start] direction[%d], mode[%d], surface is not ready, wait",
                pConfig->getMediaDirection(), pConfig->getVideoMode());
        setState(kStreamStateWaitSurface);
        return RESULT_SUCCESS;
    }

    ImsMediaResult result = startNodes();

    if (result != RESULT_SUCCESS)
    {
        setState(kStreamStateCreated);
        mCallback->SendEvent(kImsMediaEventNotifyError, result, kStreamModeRtpTx);
        return result;
    }

    setState(kStreamStateRunning);
    mVideoMode = mConfig->getVideoMode();
    return RESULT_SUCCESS;
}

bool VideoStreamGraphRtpTx::setMediaQualityThreshold(MediaQualityThreshold* threshold)
{
    if (threshold != nullptr)
    {
        BaseNode* node = findNode(kNodeIdVideoSource);

        if (node != nullptr)
        {
            IVideoSourceNode* source = reinterpret_cast<IVideoSourceNode*>(node);
            source->SetBitrateThreshold(threshold->getVideoBitrateBps());
            return true;
        }
    }

    return false;
}

void VideoStreamGraphRtpTx::setSurface(ANativeWindow* surface)
{
    IMLOGI1("[setSurface] state[%d]", mGraphState);

    if (surface != nullptr)
    {
        mSurface = surface;

        BaseNode* node = findNode(kNodeIdVideoSource);

        if (node != nullptr)
        {
            IVideoSourceNode* source = reinterpret_cast<IVideoSourceNode*>(node);
            source->UpdateSurface(surface);

            if (getState() == kStreamStateWaitSurface)
            {
                setState(kStreamStateCreated);

                if (start() != RESULT_SUCCESS)
                {
                    IMLOGE0("[setSurface] start fail");
                }
            }
        }
    }
}

ImsMediaResult VideoStreamGraphRtpTx::createPreviewMode(RtpConfig* config)
{
    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
        mConfig = nullptr;
    }

    IMLOGI0("[createPreviewMode]");
    mConfig = new VideoConfig(reinterpret_cast<VideoConfig*>(config));
    BaseNode* pNodeSource = new IVideoSourceNode(mCallback);
    pNodeSource->SetMediaType(IMS_MEDIA_VIDEO);
    pNodeSource->SetConfig(mConfig);
    AddNode(pNodeSource);

    setState(kStreamStateCreated);
    mVideoMode = VideoConfig::VIDEO_MODE_PREVIEW;
    return RESULT_SUCCESS;
}

ImsMediaResult VideoStreamGraphRtpTx::updateNodes(RtpConfig* config)
{
    IMLOGD1("[updateNodes] state[%d]", mGraphState);

    ImsMediaResult result = RESULT_NOT_READY;

    if (mGraphState == kStreamStateRunning)
    {
        mScheduler->Stop();

        for (auto& node : mListNodeStarted)
        {
            if (node != nullptr)
            {
                IMLOGD1("[updateNodes] update node[%s]", node->GetNodeName());
                result = node->UpdateConfig(config);

                if (result != RESULT_SUCCESS)
                {
                    IMLOGE2("[updateNodes] error in update node[%s], result[%d]",
                            node->GetNodeName(), result);
                    return result;
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
                IMLOGD1("[updateNodes] update node[%s]", node->GetNodeName());
                result = node->UpdateConfig(config);

                if (result != RESULT_SUCCESS)
                {
                    IMLOGE2("[updateNodes] error in update node[%s], result[%d]",
                            node->GetNodeName(), result);
                    return result;
                }
            }
        }
    }

    return result;
}

bool VideoStreamGraphRtpTx::OnEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    IMLOGI3("[OnEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);

    switch (type)
    {
        case kRequestVideoCvoUpdate:
        {
            BaseNode* node = findNode(kNodeIdRtpEncoder);

            if (node != nullptr)
            {
                RtpEncoderNode* pNode = reinterpret_cast<RtpEncoderNode*>(node);
                return pNode->SetCvoExtension(param1, param2);
            }

            return false;
        }
        break;
        case kRequestVideoBitrateChange:
        case kRequestVideoIdrFrame:
        {
            BaseNode* node = findNode(kNodeIdVideoSource);

            if (node != nullptr)
            {
                IVideoSourceNode* pNode = reinterpret_cast<IVideoSourceNode*>(node);
                pNode->OnEvent(type, param1, param2);
                return true;
            }
        }
        break;
        default:
            break;
    }

    return false;
}