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

#include <TextSession.h>
#include <ImsMediaTrace.h>
#include <ImsMediaEventHandler.h>
#include <TextConfig.h>
#include <string>
#include <sys/socket.h>

TextSession::TextSession()
{
    IMLOGD0("[TextSession]");
    mGraphRtpTx = nullptr;
    mGraphRtpRx = nullptr;
    mGraphRtcp = nullptr;
}

TextSession::~TextSession()
{
    IMLOGD0("[~TextSession]");

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

SessionState TextSession::getState()
{
    SessionState state = kSessionStateOpened;

    if ((mGraphRtpTx != nullptr && mGraphRtpTx->getState() == kStreamStateRunning) ||
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

ImsMediaResult TextSession::startGraph(RtpConfig* config)
{
    IMLOGI0("[startGraph]");

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    if (std::strcmp(pConfig->getRemoteAddress().c_str(), "") == 0)
    {
        return RESULT_INVALID_PARAM;
    }

    ImsMediaResult ret = RESULT_NOT_READY;

    if (mGraphRtpTx != nullptr)
    {
        ret = mGraphRtpTx->update(config);

        if (ret != RESULT_SUCCESS)
        {
            IMLOGE1("[startGraph] update error[%d]", ret);
            return ret;
        }
    }
    else
    {
        mGraphRtpTx = new TextStreamGraphRtpTx(this, mRtpFd);
        ret = mGraphRtpTx->create(config);

        if (ret == RESULT_SUCCESS &&
                (pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_ONLY ||
                        pConfig->getMediaDirection() == RtpConfig::MEDIA_DIRECTION_SEND_RECEIVE))
        {
            ret = mGraphRtpTx->start();

            if (ret != RESULT_SUCCESS)
            {
                IMLOGE1("[startGraph] start error[%d]", ret);
                return ret;
            }
        }
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
    }
    else
    {
        mGraphRtpRx = new TextStreamGraphRtpRx(this, mRtpFd);
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
        mGraphRtcp = new TextStreamGraphRtcp(this, mRtcpFd);
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

ImsMediaResult TextSession::sendRtt(const android::String8* text)
{
    if (mGraphRtpTx != nullptr && mGraphRtpTx->getState() == kStreamStateRunning)
    {
        mGraphRtpTx->sendRtt(text);
        return RESULT_SUCCESS;
    }

    return RESULT_NOT_READY;
}

void TextSession::onEvent(int32_t type, uint64_t param1, uint64_t param2)
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
        case kImsMediaEventMediaInactivity:
            ImsMediaEventHandler::SendEvent(
                    "TEXT_RESPONSE_EVENT", kTextMediaInactivityInd, mSessionId, param1, param2);
            break;
        case kImsMediaEventNotifyRttReceived:
            ImsMediaEventHandler::SendEvent(
                    "TEXT_RESPONSE_EVENT", kTextRttReceived, mSessionId, param1, param2);
            break;
        default:
            break;
    }
}

bool TextSession::deactivate()
{
    IMLOGI0("[deactivate]");

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

    if (getState() == kSessionStateActive)
    {
        return false;
    }

    return true;
}