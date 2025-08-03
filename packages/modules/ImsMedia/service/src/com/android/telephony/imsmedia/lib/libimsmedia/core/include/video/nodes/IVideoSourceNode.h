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

#ifndef IVIDEO_SOURCE_NODE_H_INCLUDED
#define IVIDEO_SOURCE_NODE_H_INCLUDED

#include <BaseNode.h>
#include <ImsMediaVideoSource.h>
#include <android/native_window.h>
#include <mutex>

/**
 * @brief This class is interface between audio device and ims media packetization node
 */
class IVideoSourceNode : public BaseNode, IVideoSourceCallback
{
public:
    IVideoSourceNode(BaseSessionCallback* callback = nullptr);
    virtual ~IVideoSourceNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual ImsMediaResult UpdateConfig(void* config);
    virtual void ProcessData();

    /**
     * @brief Updates preview surface
     *
     * @param window surface buffer to update
     */
    void UpdateSurface(ANativeWindow* window);

    /**
     * @brief Set the bitrate threshold to notify the indication when the encoding video bitrate is
     * less than the threshold values
     *
     * @param bitrate The video encoding bitrate in bps unit
     */
    void SetBitrateThreshold(int32_t bitrate);
    // callback from ImsMediaVideoSource
    virtual void OnUplinkEvent(uint8_t* pBitstream, uint32_t nSize, int64_t pstUsec, uint32_t flag);
    virtual void OnEvent(int32_t type, int32_t param1, int32_t param2);

protected:
    std::unique_ptr<ImsMediaVideoSource> mVideoSource;
    std::mutex mMutex;
    uint32_t mCodecType;
    uint32_t mVideoMode;
    uint32_t mCodecProfile;
    uint32_t mCodecLevel;
    uint32_t mCameraId;
    uint32_t mCameraZoom;
    uint32_t mWidth;
    uint32_t mHeight;
    uint32_t mFramerate;
    uint32_t mBitrate;
    int8_t mSamplingRate;
    uint32_t mIntraInterval;
    android::String8 mImagePath;
    uint32_t mDeviceOrientation;
    ANativeWindow* mWindow;
    int32_t mMinBitrateThreshold;
    bool mBitrateNotified;
};

#endif