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

#include <AudioStreamGraphRtpTx.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <AudioConfig.h>
#include <IAudioSourceNode.h>
#include <DtmfEncoderNode.h>
#include <DtmfSenderNode.h>
#include <AudioRtpPayloadEncoderNode.h>
#include <RtpEncoderNode.h>
#include <SocketWriterNode.h>

AudioStreamGraphRtpTx::AudioStreamGraphRtpTx(BaseSessionCallback* callback, int localFd) :
        AudioStreamGraph(callback, localFd)
{
}

AudioStreamGraphRtpTx::~AudioStreamGraphRtpTx() {}

ImsMediaResult AudioStreamGraphRtpTx::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mConfig = new AudioConfig(reinterpret_cast<AudioConfig*>(config));

    BaseNode* pNodeSource = new IAudioSourceNode(mCallback);
    pNodeSource->SetMediaType(IMS_MEDIA_AUDIO);
    pNodeSource->SetConfig(mConfig);
    AddNode(pNodeSource);

    BaseNode* pNodeRtpPayloadEncoder = new AudioRtpPayloadEncoderNode(mCallback);
    pNodeRtpPayloadEncoder->SetMediaType(IMS_MEDIA_AUDIO);
    pNodeRtpPayloadEncoder->SetConfig(mConfig);
    AddNode(pNodeRtpPayloadEncoder);
    pNodeSource->ConnectRearNode(pNodeRtpPayloadEncoder);

    BaseNode* pNodeRtpEncoder = new RtpEncoderNode(mCallback);
    pNodeRtpEncoder->SetMediaType(IMS_MEDIA_AUDIO);
    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort);
    pNodeRtpEncoder->SetConfig(mConfig);
    (static_cast<RtpEncoderNode*>(pNodeRtpEncoder))->SetLocalAddress(localAddress);
    AddNode(pNodeRtpEncoder);
    pNodeRtpPayloadEncoder->ConnectRearNode(pNodeRtpEncoder);

    BaseNode* pNodeSocketWriter = new SocketWriterNode(mCallback);
    pNodeSocketWriter->SetMediaType(IMS_MEDIA_AUDIO);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalFd(mLocalFd);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalAddress(localAddress);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetProtocolType(kProtocolRtp);
    pNodeSocketWriter->SetConfig(config);
    AddNode(pNodeSocketWriter);
    pNodeRtpEncoder->ConnectRearNode(pNodeSocketWriter);
    setState(StreamState::kStreamStateCreated);

    if (!createDtmfGraph(mConfig, pNodeRtpEncoder))
    {
        IMLOGE0("[create] fail to create dtmf graph");
    }

    return RESULT_SUCCESS;
}

ImsMediaResult AudioStreamGraphRtpTx::update(RtpConfig* config)
{
    IMLOGI1("[update] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);

    if (*reinterpret_cast<AudioConfig*>(mConfig) == *pConfig)
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
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI0("[update] pause TX");
        RtpContextParams rtpContextParams = config->getRtpContextParams();
        int32_t accessNetwork = pConfig->getAccessNetwork();

        if (accessNetwork != ACCESS_NETWORK_IWLAN &&
                mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW)
        {
            for (auto& node : mListNodeStarted)
            {
                if (node != nullptr && node->GetNodeId() == kNodeIdRtpEncoder)
                {
                    reinterpret_cast<RtpEncoderNode*>(node)->GetRtpContext(rtpContextParams);
                    pConfig->setRtpContextParams(rtpContextParams);
                }
            }
        }
        return stop();
    }

    ImsMediaResult ret = RESULT_NOT_READY;

    if (mGraphState == kStreamStateRunning)
    {
        mScheduler->Stop();
        for (auto& node : mListNodeStarted)
        {
            IMLOGD1("[update] update node[%s]", node->GetNodeName());
            ret = node->UpdateConfig(mConfig);
            if (ret != RESULT_SUCCESS)
            {
                IMLOGE2("[update] error in update node[%s], ret[%d]", node->GetNodeName(), ret);
            }
        }
        mScheduler->Start();
    }
    else if (mGraphState == kStreamStateCreated)
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
            (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
                    pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE))
    {
        IMLOGI0("[update] resume TX");
        return start();
    }

    return ret;
}

ImsMediaResult AudioStreamGraphRtpTx::start()
{
    if (mConfig == nullptr)
    {
        return RESULT_NOT_READY;
    }

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE)
    {
        return BaseStreamGraph::start();
    }

    // not started
    return RESULT_SUCCESS;
}

bool AudioStreamGraphRtpTx::createDtmfGraph(RtpConfig* config, BaseNode* rtpEncoderNode)
{
    if (config == nullptr)
    {
        return false;
    }

    AudioConfig* audioConfig = reinterpret_cast<AudioConfig*>(config);

    if (audioConfig->getTxDtmfPayloadTypeNumber() == 0)
    {
        return false;
    }

    if (mConfig == nullptr)
    {
        mConfig = new AudioConfig(*audioConfig);
    }

    BaseNode* dtmfEncoderNode = new DtmfEncoderNode(mCallback);
    dtmfEncoderNode->SetMediaType(IMS_MEDIA_AUDIO);
    dtmfEncoderNode->SetConfig(audioConfig);
    dtmfEncoderNode->SetSchedulerCallback(
            std::static_pointer_cast<StreamSchedulerCallback>(mScheduler));
    AddNode(dtmfEncoderNode);
    mListDtmfNodes.push_back(dtmfEncoderNode);

    BaseNode* dtmfSenderNode = new DtmfSenderNode(mCallback);
    dtmfSenderNode->SetMediaType(IMS_MEDIA_AUDIO);
    dtmfSenderNode->SetConfig(audioConfig);
    dtmfSenderNode->SetSchedulerCallback(
            std::static_pointer_cast<StreamSchedulerCallback>(mScheduler));
    dtmfEncoderNode->ConnectRearNode(dtmfSenderNode);
    AddNode(dtmfSenderNode);
    mListDtmfNodes.push_back(dtmfSenderNode);

    if (rtpEncoderNode != nullptr)
    {
        dtmfSenderNode->ConnectRearNode(rtpEncoderNode);
    }

    return true;
}

bool AudioStreamGraphRtpTx::sendDtmf(char digit, int duration)
{
    IMLOGD1("[sendDtmf], state[%d]", mGraphState);
    BaseNode* pDTMFNode = nullptr;
    if (!mListDtmfNodes.empty())
    {
        pDTMFNode = mListDtmfNodes.front();
    }

    if (pDTMFNode != nullptr && pDTMFNode->GetNodeId() == kNodeIdDtmfEncoder)
    {
        IMLOGD2("[sendDtmf] %c, duration[%d]", digit, duration);
        ImsMediaSubType subtype = MEDIASUBTYPE_DTMF_PAYLOAD;

        // TODO(249734476): add implementation of continuous DTMF operation
        if (duration == 0)
        {
            subtype = MEDIASUBTYPE_DTMFSTART;
        }

        pDTMFNode->OnDataFromFrontNode(subtype, (uint8_t*)&digit, 1, 0, 0, duration);
        return true;
    }
    else
    {
        IMLOGE0("[sendDtmf] DTMF is not enabled");
    }

    return false;
}

void AudioStreamGraphRtpTx::processCmr(const uint32_t cmrType, const uint32_t cmrDefine)
{
    BaseNode* node = findNode(kNodeIdAudioSource);

    if (node != nullptr)
    {
        (reinterpret_cast<IAudioSourceNode*>(node))->ProcessCmr(cmrType, cmrDefine);
    }
}

void AudioStreamGraphRtpTx::sendRtpHeaderExtension(std::list<RtpHeaderExtension>* listExtension)
{
    BaseNode* node = findNode(kNodeIdRtpEncoder);

    if (node != nullptr)
    {
        (reinterpret_cast<RtpEncoderNode*>(node))->SetRtpHeaderExtension(listExtension);
    }
}
