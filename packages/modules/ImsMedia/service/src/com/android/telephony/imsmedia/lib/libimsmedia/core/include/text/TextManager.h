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

#ifndef TEXT_MANAGER_INCLUDED
#define TEXT_MANAGER_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaEventHandler.h>
#include <BaseManager.h>
#include <TextSession.h>
#include <TextConfig.h>
#include <MediaQualityThreshold.h>
#include <android/native_window.h>
#include <unordered_map>

using namespace std;
using namespace android::telephony::imsmedia;

class TextManager : public BaseManager
{
public:
    /**
     * @brief the request handler to handle request message in an individual thread
     */
    class RequestHandler : public ImsMediaEventHandler
    {
    protected:
        virtual void processEvent(
                uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB);
    };

    /**
     * @brief the response handler to handle request message in an individual thread
     */
    class ResponseHandler : public ImsMediaEventHandler
    {
    protected:
        virtual void processEvent(
                uint32_t event, uint64_t sessionId, uint64_t paramA, uint64_t paramB);
    };

    static TextManager* getInstance();
    virtual int getState(int sessionId);
    virtual void sendMessage(const int sessionId, const android::Parcel& parcel);

protected:
    TextManager();
    virtual ~TextManager();
    ImsMediaResult openSession(
            const int sessionId, const int rtpFd, const int rtcpFd, TextConfig* config);
    ImsMediaResult closeSession(const int sessionId);
    virtual bool deactivateOtherSessionIfActive(const int sessionId);
    ImsMediaResult modifySession(const int sessionId, TextConfig* config);
    virtual void setMediaQualityThreshold(const int sessionId, MediaQualityThreshold* threshold);
    virtual ImsMediaResult sendRtt(const int sessionId, const android::String8* text);

    static TextManager* sManager;
    std::unordered_map<int, std::unique_ptr<TextSession>> mSessions;
    RequestHandler mRequestHandler;
    ResponseHandler mResponseHandler;
};

#endif
