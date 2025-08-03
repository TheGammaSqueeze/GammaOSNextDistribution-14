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

#ifndef VIDEO_SESSION_H
#define VIDEO_SESSION_H

#include <ImsMediaDefine.h>
#include <BaseSession.h>
#include <VideoStreamGraphRtpTx.h>
#include <VideoStreamGraphRtpRx.h>
#include <VideoStreamGraphRtcp.h>
#include <RtpConfig.h>
#include <android/native_window.h>

class VideoSession : public BaseSession
{
public:
    VideoSession();
    virtual ~VideoSession();
    virtual SessionState getState();
    virtual ImsMediaResult startGraph(RtpConfig* config);
    // BaseSessionCallback
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2);

    /**
     * @brief Set the preview surface
     *
     * @param surface The preview surface
     * @return ImsMediaResult Returns RESULT_SUCCESS when the surface set properly, and returns
     * RESULT_INVALID_PARAM when the parameter is not valid
     */
    ImsMediaResult setPreviewSurface(ANativeWindow* surface);

    /**
     * @brief Set the display surface
     *
     * @param surface The preview surface
     * @return ImsMediaResult Returns RESULT_SUCCESS when the surface set properly, and returns
     * RESULT_INVALID_PARAM when the parameter is not valid
     */
    ImsMediaResult setDisplaySurface(ANativeWindow* surface);

    /**
     * @brief Send internal event to process in the stream graph
     *
     * @param type The type of internal event defined in ImsMediaDefine.h
     * @param param1 The additional parameter to set
     * @param param2 The additional parameter to set
     */
    void SendInternalEvent(int32_t type, uint64_t param1, uint64_t param2);

private:
    VideoStreamGraphRtpTx* mGraphRtpTx;
    VideoStreamGraphRtpRx* mGraphRtpRx;
    VideoStreamGraphRtcp* mGraphRtcp;
    ANativeWindow* mPreviewSurface;
    ANativeWindow* mDisplaySurface;
};

#endif