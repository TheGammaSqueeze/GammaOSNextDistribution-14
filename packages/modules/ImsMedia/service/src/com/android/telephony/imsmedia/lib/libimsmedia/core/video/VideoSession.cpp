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

#include <VideoSession.h>
#include <ImsMediaTrace.h>
#include <ImsMediaEventHandler.h>
#include <VideoConfig.h>
#include <string>
#include <sys/socket.h>

VideoSession::VideoSession()
{
    IMLOGD0("[VideoSession]");
    mGraphRtpTx = nullptr;
    mGraphRtpRx = nullptr;
    mGraphRtcp = nullptr;
    mPreviewSurface = nullptr;
    mDisplaySurface = nullptr;
}

VideoSession::~VideoSession()
{
    IMLOGD0("[~VideoSession]");

    if (mGraphRtpTx != nullptr)
    {
        if (mGraphRtpTx->getState() == kStreamStateRunning)
        {
            mGraphRtpTx->stop();
        }

        delete mGraphRtpTx;
        mGraphRtpTx = nullptr;
    }

    if (mGraphRtpRx != nullptr)
    {
        if (mGraphRtpRx->getState() == kStreamStateRunning)
        {
            mGraphRtpRx->stop();
        }

        delete mGraphRtpRx;
        mGraphRtpRx = nullptr;
    }

    if (mGraphRtcp != nullptr)
    {
        if (mGraphRtcp->getState() == kStreamStateRunning)
        {
            mGraphRtcp->stop();
        }

        delete mGraphRtcp;
        mGraphRtcp = nullptr;
    }
}

SessionState VideoSession::getState()
{
    SessionState state = kSessionStateOpened;

    if ((mGraphRtpTx != nullptr && mGraphRtpTx->getState() == kStreamStateWaitSurface) ||
            (mGraphRtpRx != nullptr && mGraphRtpRx->getState() == kStreamStateWaitSurface))
    {
        return kSessionStateSuspended;
    }
    else if ((mGraphRtpTx != nullptr && mGraphRtpTx->getState() == kStreamStateRunning) ||
            (mGraphRtpRx != nullptr && mGraphRtpRx->getState() == kStreamStateRunning))
    {
        return kSessionStateActive;
    }

    if (mGraphRtcp != nullptr && mGraphRtcp->getState() == kStreamStateRunning)
    {
        return kSessionStateSuspended;
    }

    return state;
}

ImsMediaResult VideoSession::startGraph(RtpConfig* config)
{
    IMLOGI0("[startGraph]");

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    ImsMediaResult ret = RESULT_NOT_READY;

    if (mGraphRtpTx != nullptr)
    {
        mGraphRtpTx->setMediaQualityThreshold(&mThreshold);
        ret = mGraphRtpTx->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }

        if (mPreviewSurface != nullptr)
        {
            mGraphRtpTx->setSurface(mPreviewSurface);
        }
    }
    else
    {
        mGraphRtpTx = new VideoStreamGraphRtpTx(this, mRtpFd);
        ret = mGraphRtpTx->create(config);

        if (ret == RESULT_SUCCESS)
        {
            if (pConfig->getVideoMode() == VideoConfig::VIDEO_MODE_PREVIEW)
            {
                ret = mGraphRtpTx->start();
            }
            else
            {
                if (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
                        pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE)
                {
                    mGraphRtpTx->setMediaQualityThreshold(&mThreshold);
                    ret = mGraphRtpTx->start();
                }
            }

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }

            if (mPreviewSurface != nullptr)
            {
                mGraphRtpTx->setSurface(mPreviewSurface);
            }
        }
    }

    if (pConfig->getVideoMode() == VideoConfig::VIDEO_MODE_PREVIEW &&
            std::strcmp(pConfig->getRemoteAddress().c_str(), "") == 0)
    {
        return RESULT_SUCCESS;
    }

    if (mGraphRtpRx != nullptr)
    {
        mGraphRtpRx->setMediaQualityThreshold(&mThreshold);
        ret = mGraphRtpRx->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }

        if (mDisplaySurface != nullptr)
        {
            mGraphRtpRx->setSurface(mDisplaySurface);
        }
    }
    else
    {
        mGraphRtpRx = new VideoStreamGraphRtpRx(this, mRtpFd);
        ret = mGraphRtpRx->create(config);

        if (ret == RESULT_SUCCESS &&
                (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_RECEIVE_ONLY ||
                        pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE))
        {
            mGraphRtpRx->setMediaQualityThreshold(&mThreshold);
            ret = mGraphRtpRx->start();

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }

            if (mDisplaySurface != nullptr)
            {
                mGraphRtpRx->setSurface(mDisplaySurface);
            }
        }
    }

    if (mGraphRtcp != nullptr)
    {
        mGraphRtcp->setMediaQualityThreshold(&mThreshold);
        ret = mGraphRtcp->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }
    }
    else
    {
        mGraphRtcp = new VideoStreamGraphRtcp(this, mRtcpFd);
        ret = mGraphRtcp->create(config);

        if (ret == RESULT_SUCCESS)
        {
            mGraphRtcp->setMediaQualityThreshold(&mThreshold);
            ret = mGraphRtcp->start();
            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }
        }
    }

    return ret;
}

void VideoSession::onEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    IMLOGI3("[onEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);

    switch (type)
    {
        case kImsMediaEventNotifyError:
            /** TODO: need to add to send error to the client */
            break;
        case kImsMediaEventStateChanged:
            if (mState != getState())
            {
                mState = getState();
            }
            break;
        case kImsMediaEventFirstPacketReceived:
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_RESPONSE_EVENT", kVideoFirstMediaPacketInd, mSessionId, param1, param2);
            break;
        case kImsMediaEventResolutionChanged:
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_RESPONSE_EVENT", kVideoPeerDimensionChanged, mSessionId, param1, param2);
            break;
        case kImsMediaEventHeaderExtensionReceived:
            ImsMediaEventHandler::SendEvent("VIDEO_RESPONSE_EVENT", kVideoRtpHeaderExtensionInd,
                    mSessionId, param1, param2);
            break;
        case kImsMediaEventMediaInactivity:
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_RESPONSE_EVENT", kVideoMediaInactivityInd, mSessionId, param1, param2);
            break;
        case kImsMediaEventNotifyVideoDataUsage:
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_RESPONSE_EVENT", kVideoDataUsageInd, mSessionId, param1, param2);
            break;
        case kImsMediaEventNotifyVideoLowestBitrate:
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_RESPONSE_EVENT", kVideoBitrateInd, mSessionId, param1, param2);
            break;
        case kRequestVideoCvoUpdate:
        case kRequestVideoBitrateChange:
        case kRequestVideoIdrFrame:
        case kRequestVideoSendNack:
        case kRequestVideoSendPictureLost:
        case kRequestVideoSendTmmbr:
        case kRequestVideoSendTmmbn:
        case kRequestRoundTripTimeDelayUpdate:
            ImsMediaEventHandler::SendEvent(
                    "VIDEO_REQUEST_EVENT", type, mSessionId, param1, param2);
            break;
        default:
            break;
    }
}

ImsMediaResult VideoSession::setPreviewSurface(ANativeWindow* surface)
{
    if (surface == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mPreviewSurface = surface;

    if (mGraphRtpTx != nullptr)
    {
        mGraphRtpTx->setSurface(surface);
    }

    return RESULT_SUCCESS;
}

ImsMediaResult VideoSession::setDisplaySurface(ANativeWindow* surface)
{
    if (surface == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    mDisplaySurface = surface;

    if (mGraphRtpRx != nullptr)
    {
        mGraphRtpRx->setSurface(surface);
    }

    return RESULT_SUCCESS;
}

void VideoSession::SendInternalEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    IMLOGI3("[SendInternalEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);

    switch (type)
    {
        case kRequestVideoCvoUpdate:
        case kRequestVideoBitrateChange:
        case kRequestVideoIdrFrame:
            if (mGraphRtpTx != nullptr)
            {
                if (!mGraphRtpTx->OnEvent(type, param1, param2))
                {
                    IMLOGE0("[SendInternalEvent] fail to send event");
                }
            }
            break;
        case kRequestVideoSendNack:
        case kRequestVideoSendPictureLost:
        case kRequestVideoSendTmmbr:
        case kRequestVideoSendTmmbn:
            if (mGraphRtcp != nullptr)
            {
                if (!mGraphRtcp->OnEvent(type, param1, param2))
                {
                    IMLOGE0("[SendInternalEvent] fail to send event");
                }
            }
            break;
        case kRequestRoundTripTimeDelayUpdate:
            if (mGraphRtpRx != nullptr)
            {
                if (!mGraphRtpRx->OnEvent(type, param1, param2))
                {
                    IMLOGE0("[SendInternalEvent] fail to send event");
                }
            }
            break;
        default:
            break;
    }
}