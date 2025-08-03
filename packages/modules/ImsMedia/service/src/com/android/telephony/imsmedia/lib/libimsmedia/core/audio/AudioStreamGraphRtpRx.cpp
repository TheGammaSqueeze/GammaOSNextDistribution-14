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

#include <AudioStreamGraphRtpRx.h>
#include <IAudioPlayerNode.h>
#include <AudioRtpPayloadDecoderNode.h>
#include <RtpDecoderNode.h>
#include <SocketReaderNode.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <AudioConfig.h>

AudioStreamGraphRtpRx::AudioStreamGraphRtpRx(BaseSessionCallback* callback, int localFd) :
        AudioStreamGraph(callback, localFd)
{
}

AudioStreamGraphRtpRx::~AudioStreamGraphRtpRx() {}

ImsMediaResult AudioStreamGraphRtpRx::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mConfig = new AudioConfig(reinterpret_cast<AudioConfig*>(config));
    BaseNode* pNodeSocketReader = new SocketReaderNode(mCallback);
    pNodeSocketReader->SetMediaType(IMS_MEDIA_AUDIO);

    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetLocalFd(mLocalFd);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetLocalAddress(localAddress);
    (static_cast<SocketReaderNode*>(pNodeSocketReader))->SetProtocolType(kProtocolRtp);
    pNodeSocketReader->SetConfig(config);
    pNodeSocketReader->Prepare();
    AddNode(pNodeSocketReader);

    BaseNode* pNodeRtpDecoder = new RtpDecoderNode(mCallback);
    pNodeRtpDecoder->SetMediaType(IMS_MEDIA_AUDIO);
    pNodeRtpDecoder->SetConfig(mConfig);
    (static_cast<RtpDecoderNode*>(pNodeRtpDecoder))->SetLocalAddress(localAddress);
    AddNode(pNodeRtpDecoder);
    pNodeSocketReader->ConnectRearNode(pNodeRtpDecoder);

    BaseNode* pNodeRtpPayloadDecoder = new AudioRtpPayloadDecoderNode(mCallback);
    pNodeRtpPayloadDecoder->SetMediaType(IMS_MEDIA_AUDIO);
    pNodeRtpPayloadDecoder->SetConfig(mConfig);
    AddNode(pNodeRtpPayloadDecoder);
    pNodeRtpDecoder->ConnectRearNode(pNodeRtpPayloadDecoder);

    BaseNode* pNodeRenderer = new IAudioPlayerNode(mCallback);
    pNodeRenderer->SetMediaType(IMS_MEDIA_AUDIO);
    pNodeRenderer->SetConfig(mConfig);
    AddNode(pNodeRenderer);
    pNodeRtpPayloadDecoder->ConnectRearNode(pNodeRenderer);
    setState(StreamState::kStreamStateCreated);
    return RESULT_SUCCESS;
}

ImsMediaResult AudioStreamGraphRtpRx::update(RtpConfig* config)
{
    IMLOGI1("[update] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);

    if (*mConfig == *pConfig)
    {
        IMLOGI0("[update] no update");
        return RESULT_SUCCESS;
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
    }

    mConfig = new AudioConfig(pConfig);

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI0("[update] pause RX");
        return stop();
    }

    ImsMediaResult ret = RESULT_NOT_READY;
    bool needsToStart = false;

    if (mGraphState == kStreamStateRunning)
    {
        stop();
        needsToStart = true;
    }

    if (mGraphState == kStreamStateCreated)
    {
        for (auto& node : mListNodeToStart)
        {
            IMLOGD1("[update] update node[%s]", node->GetNodeName());
            ret = node->UpdateConfig(mConfig);

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE2("[update] error in update node[%s], ret[%d]", node->GetNodeName(), ret);
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

    if (needsToStart)
    {
        return start();
    }

    return ret;
}

ImsMediaResult AudioStreamGraphRtpRx::start()
{
    if (mConfig == nullptr)
    {
        return RESULT_NOT_READY;
    }

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE)
    {
        return BaseStreamGraph::start();
    }

    // not started
    return RESULT_SUCCESS;
}

void AudioStreamGraphRtpRx::processCmr(const uint32_t cmrType, const uint32_t cmrDefine)
{
    BaseNode* node = findNode(kNodeIdAudioPlayer);

    if (node != nullptr)
    {
        (reinterpret_cast<IAudioPlayerNode*>(node))->ProcessCmr(cmrType, cmrDefine);
    }
}

void AudioStreamGraphRtpRx::adjustDelay(const int32_t delayMs)
{
    BaseNode* node = findNode(kNodeIdAudioPlayer);

    if (node != nullptr)
    {
        (reinterpret_cast<IAudioPlayerNode*>(node))->AdjustDelay(delayMs);
    }
}