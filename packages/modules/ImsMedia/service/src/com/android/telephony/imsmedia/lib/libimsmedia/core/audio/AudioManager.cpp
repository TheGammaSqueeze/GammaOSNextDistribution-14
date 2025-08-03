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

#include <AudioManager.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>
#include <MediaQualityStatus.h>
#include <RtpReceptionStats.h>

using namespace android;

AudioManager* AudioManager::sManager = nullptr;

AudioManager::AudioManager()
{
    mRequestHandler.Init("AUDIO_REQUEST_EVENT");
    mResponseHandler.Init("AUDIO_RESPONSE_EVENT");
}

AudioManager::~AudioManager()
{
    mRequestHandler.Deinit();
    mResponseHandler.Deinit();
    sManager = nullptr;
}

AudioManager* AudioManager::getInstance()
{
    if (sManager == nullptr)
    {
        sManager = new AudioManager();
    }

    return sManager;
}

int AudioManager::getState(int sessionId)
{
    auto session = mSessions.find(sessionId);
    if (session != mSessions.end())
    {
        return (session->second)->getState();
    }
    else
    {
        return kSessionStateClosed;
    }
}

ImsMediaResult AudioManager::openSession(int sessionId, int rtpFd, int rtcpFd, AudioConfig* config)
{
    IMLOGI1("[openSession] sessionId[%d]", sessionId);

    if (rtpFd == -1 || rtcpFd == -1)
    {
        return RESULT_INVALID_PARAM;
    }

    if (!mSessions.count(sessionId))
    {
        std::unique_ptr<AudioSession> session(new AudioSession());
        session->setSessionId(sessionId);
        session->setLocalEndPoint(rtpFd, rtcpFd);

        if (session->startGraph(config) != RESULT_SUCCESS)
        {
            IMLOGI0("[openSession] startGraph failed");
        }

        mSessions.insert(std::make_pair(sessionId, std::move(session)));
    }
    else
    {
        return RESULT_INVALID_PARAM;
    }

    return RESULT_SUCCESS;
}

ImsMediaResult AudioManager::closeSession(int sessionId)
{
    IMLOGI1("[closeSession] sessionId[%d]", sessionId);
    if (mSessions.count(sessionId))
    {
        mSessions.erase(sessionId);
        return RESULT_SUCCESS;
    }
    return RESULT_INVALID_PARAM;
}

ImsMediaResult AudioManager::modifySession(int sessionId, AudioConfig* config)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[modifySession] sessionId[%d]", sessionId);
    if (session != mSessions.end())
    {
        if ((config->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE ||
                    config->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
                    config->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY))
        {
            if (!deactivateOtherSessionIfActive(sessionId))
            {
                return RESULT_NO_RESOURCES;
            }
        }

        if ((session->second)->IsGraphAlreadyExist(config) ||
                (session->second)->getGraphSize(kStreamRtpTx) == 0)
        {
            return (session->second)->startGraph(config);
        }
        else
        {
            return (session->second)->addGraph(config, false);
        }
    }
    else
    {
        IMLOGE1("[modifySession] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

ImsMediaResult AudioManager::addConfig(int sessionId, AudioConfig* config)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[addConfig] sessionId[%d]", sessionId);

    if (session != mSessions.end())
    {
        return (session->second)->addGraph(config, true);
    }
    else
    {
        IMLOGE1("[addConfig] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

ImsMediaResult AudioManager::deleteConfig(int sessionId, AudioConfig* config)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[deleteConfig] sessionId[%d]", sessionId);
    if (session != mSessions.end())
    {
        return (session->second)->deleteGraph(config);
    }
    else
    {
        IMLOGE1("[deleteConfig] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

ImsMediaResult AudioManager::confirmConfig(int sessionId, AudioConfig* config)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[confirmConfig] sessionId[%d]", sessionId);
    if (session != mSessions.end())
    {
        return (session->second)->confirmGraph(config);
    }
    else
    {
        IMLOGE1("[confirmConfig] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

void AudioManager::sendDtmf(int sessionId, char dtmfDigit, int duration)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[sendDtmf] sessionId[%d]", sessionId);
    if (session != mSessions.end())
    {
        (session->second)->sendDtmf(dtmfDigit, duration);
    }
    else
    {
        IMLOGE1("[sendDtmf] no session id[%d]", sessionId);
    }
}

void AudioManager::sendRtpHeaderExtension(
        int sessionId, std::list<RtpHeaderExtension>* listExtension)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[sendRtpHeaderExtension] sessionId[%d]", sessionId);
    if (session != mSessions.end())
    {
        (session->second)->sendRtpHeaderExtension(listExtension);
    }
    else
    {
        IMLOGE1("[sendRtpHeaderExtension] no session id[%d]", sessionId);
    }
}

void AudioManager::setMediaQualityThreshold(int sessionId, MediaQualityThreshold* threshold)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[setMediaQualityThreshold] sessionId[%d]", sessionId);
    if (session != mSessions.end())
    {
        (session->second)->setMediaQualityThreshold(*threshold);
    }
    else
    {
        IMLOGE1("[setMediaQualityThreshold] no session id[%d]", sessionId);
    }
}

void AudioManager::requestRtpReceptionStats(int sessionId, int intervalMs)
{
    auto session = mSessions.find(sessionId);

    if (session != mSessions.end())
    {
        IMLOGI1("[requestRtpReceptionStats] sessionId[%d]", sessionId);
        (session->second)->requestRtpReceptionStats(intervalMs);
    }
    else
    {
        IMLOGE1("[requestRtpReceptionStats] sessionId[%d] is not found", sessionId);
    }
}

void AudioManager::adjustDelay(int sessionId, int delayMs)
{
    auto session = mSessions.find(sessionId);

    if (session != mSessions.end())
    {
        IMLOGI1("[adjustDelay] sessionId[%d]", sessionId);
        (session->second)->adjustDelay(delayMs);
    }
    else
    {
        IMLOGE1("[adjustDelay] sessionId[%d] is not found", sessionId);
    }
}

void AudioManager::SendInternalEvent(
        uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[SendInternalEvent] sessionId[%d]", sessionId);

    if (session != mSessions.end())
    {
        (session->second)->SendInternalEvent(event, paramA, paramB);
    }
    else
    {
        IMLOGE1("[SendInternalEvent] no session id[%d]", sessionId);
    }
}

void AudioManager::sendMessage(const int sessionId, const android::Parcel& parcel)
{
    int nMsg = parcel.readInt32();
    status_t err = NO_ERROR;
    switch (nMsg)
    {
        case kAudioOpenSession:
        {
            int rtpFd = parcel.readInt32();
            int rtcpFd = parcel.readInt32();
            AudioConfig* config = new AudioConfig();
            err = config->readFromParcel(&parcel);

            if (err != NO_ERROR && err != -ENODATA)
            {
                IMLOGE1("[sendMessage] error readFromParcel[%d]", err);
            }

            EventParamOpenSession* param = new EventParamOpenSession(rtpFd, rtcpFd, config);
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(param));
        }
        break;
        case kAudioCloseSession:
            ImsMediaEventHandler::SendEvent("AUDIO_REQUEST_EVENT", nMsg, sessionId);
            break;
        case kAudioModifySession:
        case kAudioAddConfig:
        case kAudioConfirmConfig:
        case kAudioDeleteConfig:
        {
            AudioConfig* config = new AudioConfig();
            err = config->readFromParcel(&parcel);
            if (err != NO_ERROR)
            {
                IMLOGE1("[sendMessage] error readFromParcel[%d]", err);
            }
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(config));
        }
        break;
        case kAudioSendDtmf:
        {
            EventParamDtmf* param = new EventParamDtmf(parcel.readByte(), parcel.readInt32());
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(param));
        }
        break;
        case kAudioSendRtpHeaderExtension:
        {
            std::list<RtpHeaderExtension>* listExtension = new std::list<RtpHeaderExtension>();
            int listSize = parcel.readInt32();

            for (int32_t i = 0; i < listSize; i++)
            {
                RtpHeaderExtension extension;

                if (extension.readFromParcel(&parcel) == NO_ERROR)
                {
                    listExtension->push_back(extension);
                }
            }

            ImsMediaEventHandler::SendEvent("AUDIO_REQUEST_EVENT", nMsg, sessionId,
                    reinterpret_cast<uint64_t>(listExtension));
        }
        break;
        case kAudioSetMediaQualityThreshold:
        {
            MediaQualityThreshold* threshold = new MediaQualityThreshold();
            threshold->readFromParcel(&parcel);
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(threshold));
        }
        break;
        case kAudioRequestRtpReceptionStats:
        case kAudioAdjustDelay:
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_REQUEST_EVENT", nMsg, sessionId, parcel.readInt32());
            break;
        default:
            break;
    }
}

void AudioManager::RequestHandler::processEvent(
        uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB)
{
    IMLOGI4("[processEvent] event[%d], sessionId[%d], paramA[%d], paramB[%d]", event, sessionId,
            paramA, paramB);
    ImsMediaResult result = RESULT_SUCCESS;

    if (sManager == nullptr)
    {
        IMLOGE0("[processEvent] not ready");
        return;
    }

    switch (event)
    {
        case kAudioOpenSession:
        {
            EventParamOpenSession* param = reinterpret_cast<EventParamOpenSession*>(paramA);
            if (param != nullptr)
            {
                AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(param->mConfig);
                result = sManager->openSession(
                        static_cast<int>(sessionId), param->rtpFd, param->rtcpFd, pConfig);

                if (result == RESULT_SUCCESS)
                {
                    ImsMediaEventHandler::SendEvent(
                            "AUDIO_RESPONSE_EVENT", kAudioOpenSessionSuccess, sessionId);
                }
                else
                {
                    ImsMediaEventHandler::SendEvent(
                            "AUDIO_RESPONSE_EVENT", kAudioOpenSessionFailure, sessionId, result);
                }

                delete param;

                if (pConfig != nullptr)
                {
                    delete pConfig;
                }
            }
            else
            {
                ImsMediaEventHandler::SendEvent("AUDIO_RESPONSE_EVENT", kAudioOpenSessionFailure,
                        sessionId, RESULT_INVALID_PARAM);
            }
        }
        break;
        case kAudioCloseSession:
            if (sManager->closeSession(static_cast<int>(sessionId)) == RESULT_SUCCESS)
            {
                ImsMediaEventHandler::SendEvent(
                        "AUDIO_RESPONSE_EVENT", kAudioSessionClosed, sessionId, 0, 0);
            }
            break;
        case kAudioModifySession:
        {
            AudioConfig* config = reinterpret_cast<AudioConfig*>(paramA);
            result = sManager->modifySession(static_cast<int>(sessionId), config);
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioModifySessionResponse, sessionId, result, paramA);
        }
        break;
        case kAudioAddConfig:
        {
            AudioConfig* config = reinterpret_cast<AudioConfig*>(paramA);
            result = sManager->addConfig(static_cast<int>(sessionId), config);
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioAddConfigResponse, sessionId, result, paramA);
        }
        break;
        case kAudioConfirmConfig:
        {
            AudioConfig* config = reinterpret_cast<AudioConfig*>(paramA);
            result = sManager->confirmConfig(static_cast<int>(sessionId), config);
            ImsMediaEventHandler::SendEvent(
                    "AUDIO_RESPONSE_EVENT", kAudioConfirmConfigResponse, sessionId, result, paramA);
        }
        break;
        case kAudioDeleteConfig:
        {
            AudioConfig* config = reinterpret_cast<AudioConfig*>(paramA);
            if (config != nullptr)
            {
                sManager->deleteConfig(static_cast<int>(sessionId), config);
                delete config;
            }
        }
        break;
        case kAudioSendDtmf:
        {
            EventParamDtmf* param = reinterpret_cast<EventParamDtmf*>(paramA);
            if (param != nullptr)
            {
                sManager->sendDtmf(static_cast<int>(sessionId), param->digit, param->duration);
                delete param;
            }
        }
        break;
        case kAudioSendRtpHeaderExtension:
        {
            std::list<RtpHeaderExtension>* listExtension =
                    reinterpret_cast<std::list<RtpHeaderExtension>*>(paramA);

            if (listExtension != nullptr)
            {
                sManager->sendRtpHeaderExtension(static_cast<int>(sessionId), listExtension);
                delete listExtension;
            }
        }
        break;
        case kAudioSetMediaQualityThreshold:
        {
            MediaQualityThreshold* threshold = reinterpret_cast<MediaQualityThreshold*>(paramA);
            if (threshold != nullptr)
            {
                sManager->setMediaQualityThreshold(static_cast<int>(sessionId), threshold);
                delete threshold;
            }
        }
        break;
        case kAudioRequestRtpReceptionStats:
            sManager->requestRtpReceptionStats(static_cast<int>(sessionId), paramA);
            break;
        case kAudioAdjustDelay:
            sManager->adjustDelay(static_cast<int>(sessionId), paramA);
            break;
        case kRequestAudioCmr:
        case kRequestAudioCmrEvs:
        case kRequestSendRtcpXrReport:
            sManager->SendInternalEvent(event, static_cast<int>(sessionId), paramA, paramB);
            break;
        default:
            break;
    }
}

void AudioManager::ResponseHandler::processEvent(
        uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB)
{
    IMLOGI4("[processEvent] event[%d], sessionId[%d], paramA[%d], paramB[%d]", event, sessionId,
            paramA, paramB);

    if (sManager == nullptr)
    {
        IMLOGE0("[processEvent] not ready");
        return;
    }

    android::Parcel parcel;
    switch (event)
    {
        case kAudioOpenSessionSuccess:
        case kAudioOpenSessionFailure:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(sessionId));
            if (event == kAudioOpenSessionFailure)
            {
                // fail reason
                parcel.writeInt32(static_cast<int>(paramA));
            }
            sManager->sendResponse(sessionId, parcel);
            break;
        case kAudioModifySessionResponse:  // fall through
        case kAudioAddConfigResponse:      // fall through
        case kAudioConfirmConfigResponse:
        {
            parcel.writeInt32(event);
            parcel.writeInt32(paramA);  // result
            AudioConfig* config = reinterpret_cast<AudioConfig*>(paramB);
            if (config != nullptr)
            {
                config->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete config;
            }
        }
        break;
        case kAudioFirstMediaPacketInd:
        {
            parcel.writeInt32(event);
            AudioConfig* config = reinterpret_cast<AudioConfig*>(paramA);
            if (config != nullptr)
            {
                config->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete config;
            }
        }
        break;
        case kAudioRtpHeaderExtensionInd:
        {
            parcel.writeInt32(event);
            std::list<RtpHeaderExtension>* listExtension =
                    reinterpret_cast<std::list<RtpHeaderExtension>*>(paramA);

            if (listExtension != nullptr)
            {
                parcel.writeInt32(listExtension->size());

                for (auto& extension : *listExtension)
                {
                    extension.writeToParcel(&parcel);
                }

                sManager->sendResponse(sessionId, parcel);
                delete listExtension;
            }
        }
        break;
        case kAudioMediaQualityStatusInd:
        {
            parcel.writeInt32(event);
            MediaQualityStatus* status = reinterpret_cast<MediaQualityStatus*>(paramA);
            if (status != nullptr)
            {
                status->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete status;
            }
        }
        break;
        case kAudioTriggerAnbrQueryInd:
        {
            parcel.writeInt32(event);
            AudioConfig* status = reinterpret_cast<AudioConfig*>(paramA);
            if (status != nullptr)
            {
                status->writeToParcel(&parcel);
                AudioManager::getInstance()->sendResponse(sessionId, parcel);
                delete status;
            }
        }
            break;
        case kAudioDtmfReceivedInd:
            parcel.writeInt32(event);
            parcel.writeByte(static_cast<uint8_t>(paramA));
            parcel.writeInt32(static_cast<int>(paramB));
            sManager->sendResponse(sessionId, parcel);
            break;
        case kAudioCallQualityChangedInd:
        {
            parcel.writeInt32(event);
            CallQuality* quality = reinterpret_cast<CallQuality*>(paramA);
            if (quality != nullptr)
            {
                quality->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete quality;
            }
        }
        break;
        case kAudioSessionClosed:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(sessionId));
            sManager->sendResponse(sessionId, parcel);
            break;
        case kAudioNotifyRtpReceptionStats:
        {
            parcel.writeInt32(event);
            RtpReceptionStats* stats = reinterpret_cast<RtpReceptionStats*>(paramA);

            if (stats != nullptr)
            {
                stats->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete stats;
            }
        }
        break;
        default:
            break;
    }
}

bool AudioManager::deactivateOtherSessionIfActive(const int sessionId)
{
    for (auto const& session : mSessions)
    {
        if (session.first != sessionId)
        {
            SessionState state = (session.second)->getState();
            if (state == kSessionStateActive || state == kSessionStateReceiving ||
                    state == kSessionStateSending)
            {
                IMLOGE1("[modifySession] Another session id[%d] is active", session.first);
                if ((session.second)->deactivate())
                {
                    IMLOGI1("[modifySession] Moved session id[%d] to inactive", session.first);
                    return true;
                }
                IMLOGE1("[modifySession] Failed to move session id[%d] to inactive", session.first);
                return false;
            }
        }
    }
    return true;
}