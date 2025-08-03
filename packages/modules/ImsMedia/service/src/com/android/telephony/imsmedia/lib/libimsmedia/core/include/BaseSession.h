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

#ifndef BASE_SESSION_H
#define BASE_SESSION_H

#include <BaseSessionCallback.h>
#include <RtpConfig.h>
#include <MediaQualityThreshold.h>
#include <stdint.h>

class BaseSession : public BaseSessionCallback
{
public:
    BaseSession();
    virtual ~BaseSession();

    /** Set the session id */
    void setSessionId(const int32_t sessionId);

    /** Set the local socket file descriptor for rtp and rtcp */
    void setLocalEndPoint(const int32_t rtpFd, const int32_t rtcpFd);

    /** Get the local rtp socket file descriptor */
    int32_t getLocalRtpFd();

    /** Get the local rtcp socket file descriptor */
    int32_t getLocalRtcpFd();

    /**
     * @brief Called when the BaseSessionCallback SendEvent invoked.
     *
     * @param type The ImsMediaType type defined in ImsMediaDefine.h
     * @param param1 The parameter to set
     * @param param2 The parameter to set
     */
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2);

    /**
     * @brief Sets the media quality threshold parameters of the session to get media quality
     * notifications.
     */
    void setMediaQualityThreshold(const MediaQualityThreshold& threshold);

protected:
    /**
     * @brief get the stream state
     * @return SessionState state defined by the stream state, check #SessionState
     */
    virtual SessionState getState() = 0;
    /**
     * @brief Create and start stream graph instance. if the graph is already existed, then update
     *        graph with the RtpConfig
     *
     * @param config The parameters to operate nodes in the StreamGraph.
     * @return ImsMediaResult result of create or start graph. If the result has no error, it
     *         returns RESULT_SUCCESS. check #ImsMediaDefine.h.
     */
    virtual ImsMediaResult startGraph(RtpConfig* config) = 0;

    int mSessionId;
    int mRtpFd;
    int mRtcpFd;
    MediaQualityThreshold mThreshold;
    int mState;
};

#endif