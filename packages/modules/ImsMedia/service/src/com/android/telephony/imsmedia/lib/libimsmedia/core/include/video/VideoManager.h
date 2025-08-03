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

#ifndef VIDEO_MANAGER_INCLUDED
#define VIDEO_MANAGER_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaEventHandler.h>
#include <BaseManager.h>
#include <VideoSession.h>
#include <VideoConfig.h>
#include <MediaQualityThreshold.h>
#include <android/native_window.h>
#include <unordered_map>

using namespace std;
using namespace android::telephony::imsmedia;

class VideoManager : public BaseManager
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

    static VideoManager* getInstance();
    virtual int getState(int sessionId);
    virtual void sendMessage(const int sessionId, const android::Parcel& parcel);

    /**
     * @brief Set the Preview Surface to use to display camera preview and pause image
     *
     * @param sessionId unique identifier of the session
     * @param surface the ANativeWindow object to set
     */
    void setPreviewSurface(const int sessionId, ANativeWindow* surface);

    /**
     * @brief Set the Display Surface to use to display decoded receiving video frames
     *
     * @param sessionId unique identifier of the session
     * @param surface the ANativeWindow object to set
     */
    void setDisplaySurface(const int sessionId, ANativeWindow* surface);

    /**
     * @brief Send interval event to be handled in the StreamGraph
     *
     * @param event The event type
     * @param sessionId The session id
     */
    virtual void SendInternalEvent(
            uint32_t event, uint64_t sessionId, uint64_t paramA = 0, uint64_t paramB = 0);

protected:
    VideoManager();
    virtual ~VideoManager();
    ImsMediaResult openSession(
            const int sessionId, const int rtpFd, const int rtcpFd, VideoConfig* config);
    ImsMediaResult closeSession(const int sessionId);
    virtual ImsMediaResult setPreviewSurfaceToSession(const int sessionId, ANativeWindow* surface);
    virtual ImsMediaResult setDisplaySurfaceToSession(const int sessionId, ANativeWindow* surface);
    ImsMediaResult modifySession(const int sessionId, VideoConfig* config);
    virtual void setMediaQualityThreshold(const int sessionId, MediaQualityThreshold* threshold);

    static VideoManager* sManager;
    std::unordered_map<int, std::unique_ptr<VideoSession>> mSessions;
    RequestHandler mRequestHandler;
    ResponseHandler mResponseHandler;
};

#endif