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

#include <TextManager.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>

using namespace android;
TextManager* TextManager::sManager;

TextManager::TextManager()
{
    mRequestHandler.Init("TEXT_REQUEST_EVENT");
    mResponseHandler.Init("TEXT_RESPONSE_EVENT");
}

TextManager::~TextManager()
{
    mRequestHandler.Deinit();
    mResponseHandler.Deinit();
    sManager = nullptr;
}

TextManager* TextManager::getInstance()
{
    if (sManager == nullptr)
    {
        sManager = new TextManager();
    }

    return sManager;
}

int TextManager::getState(int sessionId)
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

ImsMediaResult TextManager::openSession(
        const int sessionId, const int rtpFd, const int rtcpFd, TextConfig* config)
{
    IMLOGI1("[openSession] sessionId[%d]", sessionId);

    if (rtpFd == -1 || rtcpFd == -1)
    {
        return RESULT_INVALID_PARAM;
    }

    if (!mSessions.count(sessionId))
    {
        std::unique_ptr<TextSession> session(new TextSession());
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

ImsMediaResult TextManager::closeSession(const int sessionId)
{
    IMLOGI1("[closeSession] sessionId[%d]", sessionId);

    if (mSessions.count(sessionId))
    {
        mSessions.erase(sessionId);
        return RESULT_SUCCESS;
    }

    return RESULT_INVALID_PARAM;
}

ImsMediaResult TextManager::modifySession(const int sessionId, TextConfig* config)
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

        return (session->second)->startGraph(config);
    }
    else
    {
        IMLOGE1("[modifySession] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

void TextManager::setMediaQualityThreshold(const int sessionId, MediaQualityThreshold* threshold)
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

ImsMediaResult TextManager::sendRtt(const int sessionId, const android::String8* text)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[sendRtt] sessionId[%d]", sessionId);

    if (session != mSessions.end())
    {
        return (session->second)->sendRtt(text);
    }

    return RESULT_INVALID_PARAM;
}

void TextManager::sendMessage(const int sessionId, const android::Parcel& parcel)
{
    int nMsg = parcel.readInt32();
    status_t err = NO_ERROR;

    switch (nMsg)
    {
        case kTextOpenSession:
        {
            int rtpFd = parcel.readInt32();
            int rtcpFd = parcel.readInt32();
            TextConfig* config = new TextConfig();
            err = config->readFromParcel(&parcel);

            if (err != NO_ERROR && err != -ENODATA)
            {
                IMLOGE1("[sendMessage] error readFromParcel[%d]", err);
            }

            EventParamOpenSession* param = new EventParamOpenSession(rtpFd, rtcpFd, config);
            ImsMediaEventHandler::SendEvent(
                    "TEXT_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(param));
        }
        break;
        case kTextCloseSession:
            ImsMediaEventHandler::SendEvent("TEXT_REQUEST_EVENT", nMsg, sessionId);
            break;
        case kTextModifySession:
        {
            TextConfig* config = new TextConfig();
            config->readFromParcel(&parcel);

            if (err != NO_ERROR)
            {
                IMLOGE1("[sendMessage] error readFromParcel[%d]", err);
            }

            ImsMediaEventHandler::SendEvent(
                    "TEXT_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(config));
        }
        break;
        case kTextSetMediaQualityThreshold:
        {
            MediaQualityThreshold* threshold = new MediaQualityThreshold();
            threshold->readFromParcel(&parcel);
            ImsMediaEventHandler::SendEvent(
                    "TEXT_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(threshold));
        }
        break;
        case kTextSendRtt:
        {
            android::String16 text;
            parcel.readString16(&text);
            android::String8* rttText = new String8(text.c_str());
            ImsMediaEventHandler::SendEvent(
                    "TEXT_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(rttText));
        }
        break;
        default:
            break;
    }
}

void TextManager::RequestHandler::processEvent(
        uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB)
{
    IMLOGI4("[processEvent] event[%d], sessionId[%d], paramA[%d], paramB[%d]", event, sessionId,
            paramA, paramB);

    if (sManager == nullptr)
    {
        return;
    }

    ImsMediaResult result = RESULT_SUCCESS;

    switch (event)
    {
        case kTextOpenSession:
        {
            EventParamOpenSession* param = reinterpret_cast<EventParamOpenSession*>(paramA);
            if (param != nullptr)
            {
                TextConfig* pConfig = reinterpret_cast<TextConfig*>(param->mConfig);
                result = sManager->openSession(
                        static_cast<int>(sessionId), param->rtpFd, param->rtcpFd, pConfig);

                if (result == RESULT_SUCCESS)
                {
                    ImsMediaEventHandler::SendEvent(
                            "TEXT_RESPONSE_EVENT", kTextOpenSessionSuccess, sessionId);
                }
                else
                {
                    ImsMediaEventHandler::SendEvent(
                            "TEXT_RESPONSE_EVENT", kTextOpenSessionFailure, sessionId, result);
                }

                delete param;

                if (pConfig != nullptr)
                {
                    delete pConfig;
                }
            }
            else
            {
                ImsMediaEventHandler::SendEvent("TEXT_RESPONSE_EVENT", kTextOpenSessionFailure,
                        sessionId, RESULT_INVALID_PARAM);
            }
        }
        break;
        case kTextCloseSession:
            if (sManager->closeSession(static_cast<int>(sessionId)) == RESULT_SUCCESS)
            {
                ImsMediaEventHandler::SendEvent(
                        "TEXT_RESPONSE_EVENT", kTextSessionClosed, sessionId, 0, 0);
            }
            break;
        case kTextModifySession:
        {
            TextConfig* config = reinterpret_cast<TextConfig*>(paramA);
            result = sManager->modifySession(static_cast<int>(sessionId), config);
            ImsMediaEventHandler::SendEvent(
                    "TEXT_RESPONSE_EVENT", kTextModifySessionResponse, sessionId, result, paramA);
        }
        break;
        case kTextSetMediaQualityThreshold:
        {
            MediaQualityThreshold* threshold = reinterpret_cast<MediaQualityThreshold*>(paramA);

            if (threshold != nullptr)
            {
                sManager->setMediaQualityThreshold(static_cast<int>(sessionId), threshold);
                delete threshold;
            }
        }
        break;
        case kTextSendRtt:
        {
            android::String8* text = reinterpret_cast<android::String8*>(paramA);

            if (text != nullptr)
            {
                sManager->sendRtt(static_cast<int>(sessionId), text);
                delete text;
            }
        }
        break;
        default:
            break;
    }
}

void TextManager::ResponseHandler::processEvent(
        uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB)
{
    IMLOGI4("[processEvent] event[%d], sessionId[%d], paramA[%d], paramB[%d]", event, sessionId,
            paramA, paramB);

    if (sManager == nullptr)
    {
        return;
    }

    android::Parcel parcel;
    switch (event)
    {
        case kTextOpenSessionSuccess:
        case kTextOpenSessionFailure:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(sessionId));

            if (event == kTextOpenSessionFailure)
            {
                // fail reason
                parcel.writeInt32(static_cast<int>(paramA));
            }

            sManager->sendResponse(sessionId, parcel);
            break;
        case kTextModifySessionResponse:  // fall through
        {
            parcel.writeInt32(event);
            parcel.writeInt32(paramA);  // result
            TextConfig* config = reinterpret_cast<TextConfig*>(paramB);

            if (config != nullptr)
            {
                config->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete config;
            }
        }
        break;
        case kTextMediaInactivityInd:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(paramA));  // type
            parcel.writeInt32(static_cast<int>(paramB));  // duration
            sManager->sendResponse(sessionId, parcel);
            break;
        case kTextRttReceived:
        {
            parcel.writeInt32(event);
            android::String8* text = reinterpret_cast<String8*>(paramA);

            if (text != nullptr)
            {
                String16 rttText(*text);
                parcel.writeString16(rttText);
                sManager->sendResponse(sessionId, parcel);
                delete text;
            }
        }
        break;
        case kTextSessionClosed:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(sessionId));
            sManager->sendResponse(sessionId, parcel);
            break;
        default:
            break;
    }
}

bool TextManager::deactivateOtherSessionIfActive(const int sessionId)
{
    for (auto const& session : mSessions)
    {
        if (session.first != sessionId)
        {
            SessionState state = (session.second)->getState();
            if (state == kSessionStateActive)
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
