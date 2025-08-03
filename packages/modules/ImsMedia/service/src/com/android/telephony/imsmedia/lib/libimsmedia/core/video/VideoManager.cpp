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

#include <VideoManager.h>
#include <ImsMediaTrace.h>
#include <ImsMediaNetworkUtil.h>

using namespace android;
VideoManager* VideoManager::sManager;

VideoManager::VideoManager()
{
    mRequestHandler.Init("VIDEO_REQUEST_EVENT");
    mResponseHandler.Init("VIDEO_RESPONSE_EVENT");
}

VideoManager::~VideoManager()
{
    mRequestHandler.Deinit();
    mResponseHandler.Deinit();
    sManager = nullptr;
}

VideoManager* VideoManager::getInstance()
{
    if (sManager == nullptr)
    {
        sManager = new VideoManager();
    }

    return sManager;
}

int VideoManager::getState(int sessionId)
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

ImsMediaResult VideoManager::openSession(
        const int sessionId, const int rtpFd, const int rtcpFd, VideoConfig* config)
{
    IMLOGI1("[openSession] sessionId[%d]", sessionId);

    if (rtpFd == -1 || rtcpFd == -1)
    {
        return RESULT_INVALID_PARAM;
    }

    if (!mSessions.count(sessionId))
    {
        std::unique_ptr<VideoSession> session(new VideoSession());
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

ImsMediaResult VideoManager::closeSession(const int sessionId)
{
    IMLOGI1("[closeSession] sessionId[%d]", sessionId);

    if (mSessions.count(sessionId))
    {
        mSessions.erase(sessionId);
        return RESULT_SUCCESS;
    }

    return RESULT_INVALID_PARAM;
}

ImsMediaResult VideoManager::setPreviewSurfaceToSession(const int sessionId, ANativeWindow* surface)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[setPreviewSurfaceToSession] sessionId[%d]", sessionId);

    if (session != mSessions.end())
    {
        return (session->second)->setPreviewSurface(surface);
    }
    else
    {
        IMLOGE1("[setPreviewSurfaceToSession] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

ImsMediaResult VideoManager::setDisplaySurfaceToSession(const int sessionId, ANativeWindow* surface)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[setDisplaySurfaceToSession] sessionId[%d]", sessionId);

    if (session != mSessions.end())
    {
        return (session->second)->setDisplaySurface(surface);
    }
    else
    {
        IMLOGE1("[setDisplaySurfaceToSession] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

ImsMediaResult VideoManager::modifySession(const int sessionId, VideoConfig* config)
{
    auto session = mSessions.find(sessionId);
    IMLOGI1("[modifySession] sessionId[%d]", sessionId);

    if (session != mSessions.end())
    {
        return (session->second)->startGraph(config);
    }
    else
    {
        IMLOGE1("[modifySession] no session id[%d]", sessionId);
        return RESULT_INVALID_PARAM;
    }
}

void VideoManager::setMediaQualityThreshold(const int sessionId, MediaQualityThreshold* threshold)
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

void VideoManager::sendMessage(const int sessionId, const android::Parcel& parcel)
{
    int nMsg = parcel.readInt32();
    status_t err = NO_ERROR;

    switch (nMsg)
    {
        case kVideoOpenSession:
        {
            int rtpFd = parcel.readInt32();
            int rtcpFd = parcel.readInt32();
            VideoConfig* config = new VideoConfig();
            err = config->readFromParcel(&parcel);

            if (err != NO_ERROR)
            {
                IMLOGE1("[sendMessage] error readFromParcel[%d]", err);
                delete config;
                config = nullptr;
            }

            EventParamOpenSession* param = new EventParamOpenSession(rtpFd, rtcpFd, config);
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(param));
        }
        break;
        case kVideoCloseSession:
            ImsMediaEventHandler::SendEvent("VIDEO_REQUEST_EVENT", nMsg, sessionId);
            break;
        case kVideoModifySession:
        {
            VideoConfig* config = new VideoConfig();
            err = config->readFromParcel(&parcel);

            if (err != NO_ERROR)
            {
                IMLOGE1("[sendMessage] error readFromParcel[%d]", err);
            }

            ImsMediaEventHandler::SendEvent(
                    "VIDEO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(config));
        }
        break;
        case kVideoSendRtpHeaderExtension:
            // TO DO
            break;
        case kVideoSetMediaQualityThreshold:
        {
            MediaQualityThreshold* threshold = new MediaQualityThreshold();
            threshold->readFromParcel(&parcel);
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_REQUEST_EVENT", nMsg, sessionId, reinterpret_cast<uint64_t>(threshold));
        }
        break;
        default:
            break;
    }
}

void VideoManager::setPreviewSurface(const int sessionId, ANativeWindow* surface)
{
    IMLOGI1("[setPreviewSurface] sessionId[%d]", sessionId);
    ImsMediaEventHandler::SendEvent("VIDEO_REQUEST_EVENT", kVideoSetPreviewSurface, sessionId,
            reinterpret_cast<uint64_t>(surface));
}

void VideoManager::setDisplaySurface(const int sessionId, ANativeWindow* surface)
{
    IMLOGI1("[setDisplaySurface] sessionId[%d]", sessionId);
    ImsMediaEventHandler::SendEvent("VIDEO_REQUEST_EVENT", kVideoSetDisplaySurface, sessionId,
            reinterpret_cast<uint64_t>(surface));
}

void VideoManager::SendInternalEvent(
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

void VideoManager::RequestHandler::processEvent(
        uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB)
{
    IMLOGI4("[processEvent] event[%d], sessionId[%d], paramA[%d], paramB[%d]", event, sessionId,
            paramA, paramB);
    ImsMediaResult result = RESULT_SUCCESS;

    if (sManager == nullptr)
    {
        return;
    }

    switch (event)
    {
        case kVideoOpenSession:
        {
            EventParamOpenSession* param = reinterpret_cast<EventParamOpenSession*>(paramA);

            if (param != nullptr)
            {
                VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(param->mConfig);
                result = sManager->openSession(
                        static_cast<int>(sessionId), param->rtpFd, param->rtcpFd, pConfig);

                if (result == RESULT_SUCCESS)
                {
                    ImsMediaEventHandler::SendEvent(
                            "VIDEO_RESPONSE_EVENT", kVideoOpenSessionSuccess, sessionId);
                }
                else
                {
                    ImsMediaEventHandler::SendEvent(
                            "VIDEO_RESPONSE_EVENT", kVideoOpenSessionFailure, sessionId, result);
                }

                delete param;

                if (pConfig != nullptr)
                {
                    delete pConfig;
                }
            }
            else
            {
                ImsMediaEventHandler::SendEvent("VIDEO_RESPONSE_EVENT", kVideoOpenSessionFailure,
                        sessionId, RESULT_INVALID_PARAM);
            }
        }
        break;
        case kVideoCloseSession:
            if (sManager->closeSession(static_cast<int>(sessionId)) == RESULT_SUCCESS)
            {
                ImsMediaEventHandler::SendEvent(
                        "VIDEO_RESPONSE_EVENT", kVideoSessionClosed, sessionId, 0, 0);
            }
            break;
        case kVideoSetPreviewSurface:
            sManager->setPreviewSurfaceToSession(
                    static_cast<int>(sessionId), reinterpret_cast<ANativeWindow*>(paramA));
            break;
        case kVideoSetDisplaySurface:
            sManager->setDisplaySurfaceToSession(
                    static_cast<int>(sessionId), reinterpret_cast<ANativeWindow*>(paramA));
            break;
        case kVideoModifySession:
        {
            VideoConfig* config = reinterpret_cast<VideoConfig*>(paramA);
            result = sManager->modifySession(static_cast<int>(sessionId), config);
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_RESPONSE_EVENT", kVideoModifySessionResponse, sessionId, result, paramA);
        }
        break;
        case kVideoSendRtpHeaderExtension:
            /** TODO: add implementation */
            break;
        case kVideoSetMediaQualityThreshold:
        {
            MediaQualityThreshold* threshold = reinterpret_cast<MediaQualityThreshold*>(paramA);

            if (threshold != nullptr)
            {
                sManager->setMediaQualityThreshold(static_cast<int>(sessionId), threshold);
                delete threshold;
            }
        }
        break;
        case kRequestVideoCvoUpdate:
        case kRequestVideoBitrateChange:
        case kRequestVideoIdrFrame:
        case kRequestVideoSendNack:
        case kRequestVideoSendPictureLost:
        case kRequestVideoSendTmmbr:
        case kRequestVideoSendTmmbn:
        case kRequestRoundTripTimeDelayUpdate:
            sManager->SendInternalEvent(event, sessionId, paramA, paramB);
            break;
        default:
            break;
    }
}

void VideoManager::ResponseHandler::processEvent(
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
        case kVideoOpenSessionSuccess:
        case kVideoOpenSessionFailure:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(sessionId));

            if (event == kVideoOpenSessionFailure)
            {
                // fail reason
                parcel.writeInt32(static_cast<int>(paramA));
            }

            sManager->sendResponse(sessionId, parcel);
            break;
        case kVideoModifySessionResponse:  // fall through
        {
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(paramA));  // result
            VideoConfig* config = reinterpret_cast<VideoConfig*>(paramB);

            if (config != nullptr)
            {
                config->writeToParcel(&parcel);
                sManager->sendResponse(sessionId, parcel);
                delete config;
            }
        }
        break;
        case kVideoFirstMediaPacketInd:
            parcel.writeInt32(event);
            sManager->sendResponse(sessionId, parcel);
            break;
        case kVideoPeerDimensionChanged:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(paramA));
            parcel.writeInt32(static_cast<int>(paramB));
            sManager->sendResponse(sessionId, parcel);
            break;
        case kVideoRtpHeaderExtensionInd:
            // TODO : add implementation
            break;
        case kVideoMediaInactivityInd:
        case kVideoBitrateInd:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(paramA));
            sManager->sendResponse(sessionId, parcel);
            break;
        case kVideoDataUsageInd:
            parcel.writeInt32(event);
            parcel.writeInt64(static_cast<int>(paramA));
            sManager->sendResponse(sessionId, parcel);
            break;
        case kVideoSessionClosed:
            parcel.writeInt32(event);
            parcel.writeInt32(static_cast<int>(sessionId));
            sManager->sendResponse(sessionId, parcel);
            break;
        default:
            break;
    }
}
