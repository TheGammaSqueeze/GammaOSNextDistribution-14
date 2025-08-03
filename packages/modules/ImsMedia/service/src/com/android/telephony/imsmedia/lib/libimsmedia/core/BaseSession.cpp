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

#include <BaseSession.h>
#include <ImsMediaTrace.h>
#include <ImsMediaEventHandler.h>
#include <string.h>
#include <ImsMediaNetworkUtil.h>

BaseSession::BaseSession() :
        mRtpFd(-1),
        mRtcpFd(-1),
        mState(kSessionStateClosed)
{
}

BaseSession::~BaseSession()
{
    if (mRtpFd != -1)
    {
        IMLOGD0("[~BaseSession] close rtp fd");
        ImsMediaNetworkUtil::closeSocket(mRtpFd);
    }

    if (mRtcpFd != -1)
    {
        IMLOGD0("[~BaseSession] close rtcp fd");
        ImsMediaNetworkUtil::closeSocket(mRtcpFd);
    }
}

void BaseSession::setSessionId(int sessionId)
{
    mSessionId = sessionId;
}

void BaseSession::setLocalEndPoint(int rtpFd, int rtcpFd)
{
    IMLOGI2("[setLocalEndPoint] rtpFd[%d], rtcpFd[%d]", rtpFd, rtcpFd);
    mRtpFd = rtpFd;
    mRtcpFd = rtcpFd;
}

int32_t BaseSession::getLocalRtpFd()
{
    return mRtpFd;
}

int32_t BaseSession::getLocalRtcpFd()
{
    return mRtcpFd;
}

void BaseSession::onEvent(int32_t type, uint64_t param1, uint64_t param2)
{
    IMLOGI3("[onEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);
}

void BaseSession::setMediaQualityThreshold(const MediaQualityThreshold& threshold)
{
    IMLOGI0("[setMediaQualityThreshold]");
    mThreshold = threshold;
}