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

#include <TextStreamGraphRtpTx.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <TextConfig.h>
#include <RtpEncoderNode.h>
#include <SocketWriterNode.h>
#include <TextRtpPayloadEncoderNode.h>
#include <TextSourceNode.h>

TextStreamGraphRtpTx::TextStreamGraphRtpTx(BaseSessionCallback* callback, int localFd) :
        TextStreamGraph(callback, localFd)
{
}

TextStreamGraphRtpTx::~TextStreamGraphRtpTx() {}

ImsMediaResult TextStreamGraphRtpTx::create(RtpConfig* config)
{
    IMLOGI1("[create] state[%d]", mGraphState);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    if (mConfig != nullptr)
    {
        delete mConfig;
        mConfig = nullptr;
    }

    mConfig = new TextConfig(pConfig);

    char localIp[MAX_IP_LEN];
    uint32_t localPort = 0;
    ImsMediaNetworkUtil::getLocalIpPortFromSocket(mLocalFd, localIp, MAX_IP_LEN, localPort);
    RtpAddress localAddress(localIp, localPort);

    BaseNode* pNodeSource = new TextSourceNode(mCallback);
    pNodeSource->SetMediaType(IMS_MEDIA_TEXT);
    pNodeSource->SetConfig(mConfig);
    AddNode(pNodeSource);

    BaseNode* pNodeRtpPayloadEncoder = new TextRtpPayloadEncoderNode(mCallback);
    pNodeRtpPayloadEncoder->SetMediaType(IMS_MEDIA_TEXT);
    pNodeRtpPayloadEncoder->SetConfig(mConfig);
    AddNode(pNodeRtpPayloadEncoder);
    pNodeSource->ConnectRearNode(pNodeRtpPayloadEncoder);

    BaseNode* pNodeRtpEncoder = new RtpEncoderNode(mCallback);
    pNodeRtpEncoder->SetMediaType(IMS_MEDIA_TEXT);
    pNodeRtpEncoder->SetConfig(mConfig);
    (static_cast<RtpEncoderNode*>(pNodeRtpEncoder))->SetLocalAddress(localAddress);
    AddNode(pNodeRtpEncoder);
    pNodeRtpPayloadEncoder->ConnectRearNode(pNodeRtpEncoder);

    BaseNode* pNodeSocketWriter = new SocketWriterNode(mCallback);
    pNodeSocketWriter->SetMediaType(IMS_MEDIA_TEXT);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalFd(mLocalFd);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetLocalAddress(localAddress);
    (static_cast<SocketWriterNode*>(pNodeSocketWriter))->SetProtocolType(kProtocolRtp);
    pNodeSocketWriter->SetConfig(config);
    AddNode(pNodeSocketWriter);
    pNodeRtpEncoder->ConnectRearNode(pNodeSocketWriter);

    setState(StreamState::kStreamStateCreated);
    return RESULT_SUCCESS;
}

ImsMediaResult TextStreamGraphRtpTx::update(RtpConfig* config)
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
        mConfig = nullptr;
    }

    ImsMediaResult ret = RESULT_NOT_READY;

    mConfig = new TextConfig(pConfig);

    if (mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
            mConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI0("[update] pause TX");

        RtpContextParams rtpContextParams = config->getRtpContextParams();
        int32_t accessNetwork = pConfig->getAccessNetwork();
        // TODO: #include <aidl/android/hardware/radio/AccessNetwork.h>
        if (accessNetwork != 5 &&
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

    ret = RESULT_NOT_READY;

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
            (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
                    pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE))
    {
        IMLOGI0("[update] resume TX");
        return start();
    }

    return ret;
}

ImsMediaResult TextStreamGraphRtpTx::start()
{
    IMLOGI1("[start] state[%d]", mGraphState);

    if (mConfig == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(mConfig);

    if (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW ||
            pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
            pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_INACTIVE)
    {
        IMLOGI1("[start] direction[%d] no need to start", pConfig->getMediaDirection());
        return RESULT_SUCCESS;
    }

    ImsMediaResult result = startNodes();

    if (result != RESULT_SUCCESS)
    {
        setState(StreamState::kStreamStateCreated);
        mCallback->SendEvent(kImsMediaEventNotifyError, result, kStreamModeRtpTx);
        return result;
    }

    setState(StreamState::kStreamStateRunning);
    return RESULT_SUCCESS;
}

bool TextStreamGraphRtpTx::sendRtt(const android::String8* text)
{
    IMLOGD1("[sendRtt], state[%d]", mGraphState);
    TextSourceNode* node = nullptr;

    if (!mListNodeStarted.empty())
    {
        node = reinterpret_cast<TextSourceNode*>(mListNodeStarted.front());
    }

    if (node != nullptr)
    {
        node->SendRtt(text);
        return true;
    }

    return false;
}