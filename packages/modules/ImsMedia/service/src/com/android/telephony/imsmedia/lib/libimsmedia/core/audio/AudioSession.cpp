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

#include <AudioSession.h>
#include <ImsMediaTrace.h>
#include <ImsMediaEventHandler.h>
#include <ImsMediaAudioUtil.h>
#include <AudioConfig.h>
#include <string>

AudioSession::AudioSession()
{
    IMLOGD0("[AudioSession]");
    std::unique_ptr<MediaQualityAnalyzer> analyzer(new MediaQualityAnalyzer());
    mMediaQualityAnalyzer = std::move(analyzer);
    mMediaQualityAnalyzer->setCallback(this);
}

AudioSession::~AudioSession()
{
    IMLOGD0("[~AudioSession]");

    mMediaQualityAnalyzer->stop();

    while (mListGraphRtpTx.size() > 0)
    {
        AudioStreamGraphRtpTx* graph = mListGraphRtpTx.front();

        if (graph->getState() == kStreamStateRunning)
        {
            graph->stop();
        }

        mListGraphRtpTx.pop_front();
        delete graph;
    }

    while (mListGraphRtpRx.size() > 0)
    {
        AudioStreamGraphRtpRx* graph = mListGraphRtpRx.front();

        if (graph->getState() == kStreamStateRunning)
        {
            graph->stop();
        }

        mListGraphRtpRx.pop_front();
        delete graph;
    }

    while (mListGraphRtcp.size() > 0)
    {
        AudioStreamGraphRtcp* graph = mListGraphRtcp.front();

        if (graph->getState() == kStreamStateRunning)
        {
            graph->stop();
        }

        mListGraphRtcp.pop_front();
        delete graph;
    }
}

SessionState AudioSession::getState()
{
    SessionState state = kSessionStateOpened;

    for (auto& graph : mListGraphRtpTx)
    {
        if (graph != nullptr && graph->getState() == kStreamStateRunning)
        {
            state = kSessionStateSending;
            break;
        }
    }

    for (auto& graph : mListGraphRtpRx)
    {
        if (graph != nullptr && graph->getState() == kStreamStateRunning)
        {
            return state == kSessionStateSending ? kSessionStateActive : kSessionStateReceiving;
        }
    }

    for (auto& graph : mListGraphRtcp)
    {
        if (graph != nullptr && graph->getState() == kStreamStateRunning)
        {
            return state == kSessionStateSending ? kSessionStateSending : kSessionStateSuspended;
        }
    }

    return state;
}

ImsMediaResult AudioSession::startGraph(RtpConfig* config)
{
    IMLOGI0("[startGraph]");

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    AudioConfig* audioConfig = reinterpret_cast<AudioConfig*>(config);

    if (std::strcmp(audioConfig->getRemoteAddress().c_str(), "") == 0)
    {
        return RESULT_INVALID_PARAM;
    }

    IMLOGI1("[startGraph] state[%d]", getState());

    if (mMediaQualityAnalyzer != nullptr)
    {
        if (audioConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_NO_FLOW)
        {
            mMediaQualityAnalyzer->stop();
        }
        else
        {
            mMediaQualityAnalyzer->setConfig(audioConfig);
            mMediaQualityAnalyzer->start();
        }
    }

    ImsMediaResult ret = RESULT_NOT_READY;
    IMLOGD1("[startGraph] mListGraphRtpTx size[%d]", mListGraphRtpTx.size());

    AudioStreamGraphRtpTx* graphTx = AudioStreamGraph::findGraph(mListGraphRtpTx,
            [&config](AudioStreamGraphRtpTx* graph)
            {
                return graph != nullptr && graph->isSameGraph(config);
            });

    if (graphTx != nullptr)
    {
        ret = graphTx->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }
    }
    else
    {
        mListGraphRtpTx.push_back(new AudioStreamGraphRtpTx(this, mRtpFd));

        if (mListGraphRtpTx.back()->create(config) == RESULT_SUCCESS)
        {
            ret = mListGraphRtpTx.back()->start();

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }
        }
    }

    IMLOGD1("[startGraph] ListGraphRtpRx size[%d]", mListGraphRtpRx.size());

    AudioStreamGraphRtpRx* graphRx = AudioStreamGraph::findGraph(mListGraphRtpRx,
            [&config](AudioStreamGraphRtpRx* graph)
            {
                return graph != nullptr && graph->isSameGraph(config);
            });

    if (graphRx != nullptr)
    {
        graphRx->setMediaQualityThreshold(&mThreshold);
        ret = graphRx->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }
    }
    else
    {
        mListGraphRtpRx.push_back(new AudioStreamGraphRtpRx(this, mRtpFd));

        if (mListGraphRtpRx.back()->create(config) == RESULT_SUCCESS)
        {
            mListGraphRtpRx.back()->setMediaQualityThreshold(&mThreshold);
            ret = mListGraphRtpRx.back()->start();

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }
        }
    }

    IMLOGD1("[startGraph] mListGraphRtcp size[%d]", mListGraphRtcp.size());

    AudioStreamGraphRtcp* graphRtcp = AudioStreamGraph::findGraph(mListGraphRtcp,
            [&config](AudioStreamGraphRtcp* graph)
            {
                return graph != nullptr && graph->isSameGraph(config);
            });

    if (graphRtcp != nullptr)
    {
        graphRtcp->setMediaQualityThreshold(&mThreshold);
        ret = graphRtcp->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }
    }
    else
    {
        mListGraphRtcp.push_back(new AudioStreamGraphRtcp(this, mRtcpFd));

        if (mListGraphRtcp.back()->create(config) == RESULT_SUCCESS)
        {
            mListGraphRtcp.back()->setMediaQualityThreshold(&mThreshold);
            ret = mListGraphRtcp.back()->start();

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }
        }
    }

    return ret;
}

ImsMediaResult AudioSession::addGraph(RtpConfig* config, bool enableRtcp)
{
    IMLOGD1("[addGraph], enable rtcp[%d]", enableRtcp);

    if (config == nullptr || std::strcmp(config->getRemoteAddress().c_str(), "") == 0)
    {
        return RESULT_INVALID_PARAM;
    }

    if (IsGraphAlreadyExist(config) || mListGraphRtpTx.empty())
    {
        return startGraph(config);
    }

    if (enableRtcp)  // update current graph to inactive mode
    {
        for (auto& graph : mListGraphRtpTx)
        {
            if (graph != nullptr)
            {
                graph->stop();
            }
        }

        for (auto& graph : mListGraphRtpRx)
        {
            if (graph != nullptr)
            {
                graph->stop();
            }
        }

        for (auto& graph : mListGraphRtcp)
        {
            if (graph != nullptr && graph->getState() != kStreamStateRunning)
            {
                graph->start();
            }
        }

        return startGraph(config);
    }
    else
    {
        return confirmGraph(config);
    }
}

ImsMediaResult AudioSession::confirmGraph(RtpConfig* config)
{
    if (config == nullptr || std::strcmp(config->getRemoteAddress().c_str(), "") == 0)
    {
        return RESULT_INVALID_PARAM;
    }

    if (mListGraphRtpTx.empty() || mListGraphRtpRx.empty() || mListGraphRtcp.empty())
    {
        return startGraph(config);
    }

    /** Stop and delete unmatched running instances of StreamGraph. */
    for (std::list<AudioStreamGraphRtpTx*>::iterator iter = mListGraphRtpTx.begin();
            iter != mListGraphRtpTx.end();)
    {
        AudioStreamGraphRtpTx* graph = *iter;

        if (graph != nullptr && !graph->isSameGraph(config))
        {
            graph->stop();
            iter = mListGraphRtpTx.erase(iter);
            delete graph;
        }
        else
        {
            iter++;
        }
    }

    IMLOGD1("[confirmGraph] mListGraphTx size[%d]", mListGraphRtpTx.size());

    for (std::list<AudioStreamGraphRtpRx*>::iterator iter = mListGraphRtpRx.begin();
            iter != mListGraphRtpRx.end();)
    {
        AudioStreamGraphRtpRx* graph = *iter;

        if (graph != nullptr && !graph->isSameGraph(config))
        {
            graph->stop();
            iter = mListGraphRtpRx.erase(iter);
            delete graph;
        }
        else
        {
            iter++;
        }
    }

    IMLOGD1("[confirmGraph] mListGraphRx size[%d]", mListGraphRtpRx.size());

    for (std::list<AudioStreamGraphRtcp*>::iterator iter = mListGraphRtcp.begin();
            iter != mListGraphRtcp.end();)
    {
        AudioStreamGraphRtcp* graph = *iter;

        if (graph != nullptr && !graph->isSameGraph(config))
        {
            graph->stop();
            iter = mListGraphRtcp.erase(iter);
            delete graph;
        }
        else
        {
            iter++;
        }
    }

    IMLOGD1("[confirmGraph] mListGraphRtcp size[%d]", mListGraphRtcp.size());

    return startGraph(config);
}

ImsMediaResult AudioSession::deleteGraph(RtpConfig* config)
{
    IMLOGI0("[deleteGraph]");
    bool bFound = false;

    for (std::list<AudioStreamGraphRtpTx*>::iterator iter = mListGraphRtpTx.begin();
            iter != mListGraphRtpTx.end();)
    {
        AudioStreamGraphRtpTx* graph = *iter;

        if (graph == nullptr)
        {
            continue;
        }

        if (graph->isSameGraph(config))
        {
            graph->stop();
            iter = mListGraphRtpTx.erase(iter);
            delete graph;
            bFound = true;
            break;
        }
        else
        {
            iter++;
        }
    }

    if (bFound == false)
    {
        return RESULT_INVALID_PARAM;
    }

    IMLOGD1("[deleteGraph] mListGraphRtpTx size[%d]", mListGraphRtpTx.size());

    for (std::list<AudioStreamGraphRtpRx*>::iterator iter = mListGraphRtpRx.begin();
            iter != mListGraphRtpRx.end();)
    {
        AudioStreamGraphRtpRx* graph = *iter;

        if (graph == nullptr)
        {
            continue;
        }

        if (graph->isSameGraph(config))
        {
            if (graph->getState() == kStreamStateRunning)
            {
                graph->stop();
            }

            iter = mListGraphRtpRx.erase(iter);
            delete graph;
            break;
        }
        else
        {
            iter++;
        }
    }

    IMLOGD1("[deleteGraph] mListGraphRtpRx size[%d]", mListGraphRtpRx.size());

    for (std::list<AudioStreamGraphRtcp*>::iterator iter = mListGraphRtcp.begin();
            iter != mListGraphRtcp.end();)
    {
        AudioStreamGraphRtcp* graph = *iter;

        if (graph == nullptr)
        {
            continue;
        }

        if (graph->isSameGraph(config))
        {
            if (graph->getState() == kStreamStateRunning)
            {
                graph->stop();
            }

            iter = mListGraphRtcp.erase(iter);
            delete graph;
            break;
        }
        else
        {
            iter++;
        }
    }

    IMLOGD1("[deleteGraph] mListGraphRtcp size[%d]", mListGraphRtcp.size());
    return RESULT_SUCCESS;
}

void AudioSession::onEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    switch (type)
    {
        case kImsMediaEventStateChanged:
            if (mState != getState())
            {
                mState = getState();
            }
            break;
        case kImsMediaEventNotifyError:
            break;
        case kImsMediaEventFirstPacketReceived:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioFirstMediaPacketInd, mSessionId, param1, param2);
            break;
        case kImsMediaEventHeaderExtensionReceived:
            ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioRtpHeaderExtensionInd,
                    mSessionId, param1, param2);
            break;
        case kImsMediaEventMediaQualityStatus:
            ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioMediaQualityStatusInd,
                    mSessionId, param1, param2);
            break;
        case kAudioTriggerAnbrQueryInd:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioTriggerAnbrQueryInd, mSessionId, param1);
            break;
        case kAudioDtmfReceivedInd:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioDtmfReceivedInd, mSessionId, param1, param2);
            break;
        case kAudioCallQualityChangedInd:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioCallQualityChangedInd, mSessionId, param1);
            break;
        case kAudioNotifyRtpReceptionStats:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioNotifyRtpReceptionStats, mSessionId, param1);
            break;
        case kRequestAudioCmr:
        case kRequestAudioCmrEvs:
        case kRequestSendRtcpXrReport:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_REQUEST_EVENT", type, mSessionId, param1, param2);
            break;
        case kRequestRoundTripTimeDelayUpdate:
        case kRequestAudioPlayingStatus:
        case kCollectPacketInfo:
        case kCollectOptionalInfo:
        case kCollectRxRtpStatus:
        case kCollectJitterBufferSize:
        case kGetRtcpXrReportBlock:
            if (mMediaQualityAnalyzer != nullptr)
            {
                mMediaQualityAnalyzer->SendEvent(type, param1, param2);
            }
            else
            {
                switch (type)
                {
                    case kCollectRxRtpStatus:
                    case kCollectOptionalInfo:
                        if (param1)
                        {
                            delete reinterpret_cast<SessionCallbackParameter*>(param1);
                        }
                        break;

                    case kCollectPacketInfo:
                        if (param1 == kStreamRtcp)
                        {
                            if (param2)
                            {
                                delete reinterpret_cast<RtcpSr*>(param2);
                            }
                        }
                        else if (param2)
                        {
                            delete reinterpret_cast<RtpPacket*>(param2);
                        }
                        break;
                }
            }
            break;
        default:
            break;
    }
}

void AudioSession::setMediaQualityThreshold(const MediaQualityThreshold& threshold)
{
    IMLOGI0("[setMediaQualityThreshold]");
    mThreshold = threshold;

    if (mMediaQualityAnalyzer != nullptr)
    {
        mMediaQualityAnalyzer->setMediaQualityThreshold(threshold);
    }
}

void AudioSession::sendDtmf(char digit, int duration)
{
    if (mListGraphRtpTx.empty())
    {
        return;
    }

    for (std::list<AudioStreamGraphRtpTx*>::iterator iter = mListGraphRtpTx.begin();
            iter != mListGraphRtpTx.end(); iter++)
    {
        AudioStreamGraphRtpTx* graph = *iter;

        if (graph != nullptr && graph->getState() == kStreamStateRunning)
        {
            graph->sendDtmf(digit, duration);
        }
    }
}

void AudioSession::requestRtpReceptionStats(int32_t intervalMs)
{
    if (mMediaQualityAnalyzer != nullptr)
    {
        mMediaQualityAnalyzer->setNotifyRtpReceptionStatsInterval(intervalMs);
    }
}

void AudioSession::adjustDelay(const int32_t delayMs)
{
    if (mListGraphRtpRx.empty())
    {
        return;
    }

    for (std::list<AudioStreamGraphRtpRx*>::iterator iter = mListGraphRtpRx.begin();
            iter != mListGraphRtpRx.end(); iter++)
    {
        AudioStreamGraphRtpRx* graph = *iter;

        if (graph != nullptr && graph->getState() == kStreamStateRunning)
        {
            graph->adjustDelay(delayMs);
        }
    }
}

bool AudioSession::IsGraphAlreadyExist(RtpConfig* config)
{
    if (mListGraphRtpTx.size() != 0)
    {
        for (auto& graph : mListGraphRtpTx)
        {
            if (graph != nullptr && graph->isSameGraph(config))
            {
                return true;
            }
        }
    }

    return false;
}

uint32_t AudioSession::getGraphSize(ImsMediaStreamType type)
{
    switch (type)
    {
        case kStreamRtpTx:
            return mListGraphRtpTx.size();
        case kStreamRtpRx:
            return mListGraphRtpRx.size();
        case kStreamRtcp:
            return mListGraphRtcp.size();
    }

    return 0;
}

void AudioSession::sendRtpHeaderExtension(std::list<RtpHeaderExtension>* listExtension)
{
    for (auto& graph : mListGraphRtpTx)
    {
        if (graph != nullptr)
        {
            graph->sendRtpHeaderExtension(listExtension);
        }
    }
}

void AudioSession::SendInternalEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    switch (type)
    {
        case kRequestAudioCmr:
        case kRequestAudioCmrEvs:
            for (std::list<AudioStreamGraphRtpTx*>::iterator iter = mListGraphRtpTx.begin();
                    iter != mListGraphRtpTx.end(); iter++)
            {
                AudioStreamGraphRtpTx* graph = *iter;

                if (graph != nullptr && graph->getState() == kStreamStateRunning)
                {
                    type == kRequestAudioCmr ? graph->processCmr(static_cast<uint32_t>(param1))
                                             : graph->processCmr(static_cast<uint32_t>(param1),
                                                       static_cast<uint32_t>(param2));
                }
            }

            if (type == kRequestAudioCmrEvs)
            {
                for (std::list<AudioStreamGraphRtpRx*>::iterator iter = mListGraphRtpRx.begin();
                        iter != mListGraphRtpRx.end(); iter++)
                {
                    AudioStreamGraphRtpRx* graph = *iter;

                    if (graph != nullptr && graph->getState() == kStreamStateRunning)
                    {
                        graph->processCmr(
                                static_cast<uint32_t>(param1), static_cast<uint32_t>(param2));
                    }
                }
            }
            break;
        case kRequestSendRtcpXrReport:
            for (std::list<AudioStreamGraphRtcp*>::iterator iter = mListGraphRtcp.begin();
                    iter != mListGraphRtcp.end(); iter++)
            {
                AudioStreamGraphRtcp* graph = *iter;

                if (graph != nullptr && graph->getState() == kStreamStateRunning)
                {
                    graph->OnEvent(type, param1, param2);
                }
            }
            break;
        default:
            break;
    }
}

bool AudioSession::deactivate()
{
    IMLOGI0("[deactivate]");

    for (auto& graph : mListGraphRtpTx)
    {
        if (graph != nullptr)
        {
            graph->stop();
        }
    }

    for (auto& graph : mListGraphRtpRx)
    {
        if (graph != nullptr)
        {
            graph->stop();
        }
    }

    SessionState state = getState();
    if (state == kSessionStateActive || state == kSessionStateReceiving ||
            state == kSessionStateSending)
    {
        return false;
    }

    return true;
}
