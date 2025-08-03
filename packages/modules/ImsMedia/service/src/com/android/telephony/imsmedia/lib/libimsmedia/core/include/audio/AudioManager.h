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

#ifndef AUDIO_MANAGER_INCLUDED
#define AUDIO_MANAGER_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaEventHandler.h>
#include <BaseManager.h>
#include <AudioSession.h>
#include <AudioConfig.h>
#include <MediaQualityThreshold.h>
#include <RtpHeaderExtension.h>
#include <unordered_map>

using namespace std;
using namespace android::telephony::imsmedia;

class AudioManager : public BaseManager
{
public:
    /**
     * @brief   Request handler to handle request message in an individual thread
     */
    class RequestHandler : public ImsMediaEventHandler
    {
    protected:
        virtual void processEvent(
                uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB);
    };

    /**
     * @brief   response handler to handle request message in an individual thread
     */
    class ResponseHandler : public ImsMediaEventHandler
    {
    protected:
        virtual void processEvent(
                uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB);
    };

    static AudioManager* getInstance();
    virtual int getState(int sessionId);
    virtual void sendMessage(const int sessionId, const android::Parcel& parcel);

protected:
    AudioManager();
    virtual ~AudioManager();
    ImsMediaResult openSession(int sessionId, int rtpFd, int rtcpFd, AudioConfig* config);
    ImsMediaResult closeSession(int sessionId);
    ImsMediaResult modifySession(int sessionId, AudioConfig* config);
    ImsMediaResult addConfig(int sessionId, AudioConfig* config);
    virtual ImsMediaResult deleteConfig(int sessionId, AudioConfig* config);
    ImsMediaResult confirmConfig(int sessionId, AudioConfig* config);
    virtual void sendDtmf(int sessionId, char dtmfDigit, int duration);
    virtual void sendRtpHeaderExtension(
            int sessionId, std::list<RtpHeaderExtension>* listExtension);
    virtual void setMediaQualityThreshold(int sessionId, MediaQualityThreshold* threshold);
    virtual void requestRtpReceptionStats(int sessionId, int intervalMs);
    virtual void adjustDelay(int sessionId, int delayMs);
    virtual void SendInternalEvent(
            uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB);
    virtual bool deactivateOtherSessionIfActive(const int sessionId);

    static AudioManager* sManager;
    std::unordered_map<int, std::unique_ptr<AudioSession>> mSessions;
    RequestHandler mRequestHandler;
    ResponseHandler mResponseHandler;
};

#endif