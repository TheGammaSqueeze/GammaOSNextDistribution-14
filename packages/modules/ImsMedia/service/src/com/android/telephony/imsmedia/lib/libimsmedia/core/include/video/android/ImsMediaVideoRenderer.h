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

#ifndef IMSMEDIA_VIDEO_RENDERER_H_INCLUDED
#define IMSMEDIA_VIDEO_RENDERER_H_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaCondition.h>
#include <BaseSessionCallback.h>
#include <android/native_window.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <mutex>
#include <list>

struct FrameData
{
public:
    FrameData(uint8_t* data = nullptr, uint32_t size = 0, uint32_t timestamp = 0,
            bool isConfig = false)
    {
        this->data = nullptr;

        if (size != 0 && data != nullptr)
        {
            this->data = new uint8_t[size];
            memcpy(this->data, data, size);
        }

        this->size = size;
        this->timestamp = timestamp;
        this->isConfig = isConfig;
    }

    ~FrameData()
    {
        if (data != nullptr)
        {
            delete[] data;
        }
    }

    uint8_t* data;
    uint32_t size;
    uint32_t timestamp;
    bool isConfig;
};

/**
 * @brief This class uses android video renderer interface to display video frames.
 */
class ImsMediaVideoRenderer
{
public:
    ImsMediaVideoRenderer();
    ~ImsMediaVideoRenderer();
    void SetSessionCallback(BaseSessionCallback* callback);
    void SetCodec(int32_t codecType);
    void SetResolution(uint32_t width, uint32_t height);
    void SetDeviceOrientation(uint32_t orientation);
    void SetSurface(ANativeWindow* window);
    bool Start();
    void Stop();
    void OnDataFrame(uint8_t* data, uint32_t size, uint32_t timestamp, bool isConfigFrame);
    void processBuffers();
    void UpdateDeviceOrientation(uint32_t degree);
    void UpdatePeerOrientation(uint32_t degree);

private:
    BaseSessionCallback* mCallback;
    ANativeWindow* mWindow;
    AMediaCodec* mCodec;
    AMediaFormat* mFormat;
    std::list<FrameData*> mFrameDatas;
    std::mutex mMutex;
    ImsMediaCondition mConditionExit;
    int32_t mCodecType;
    uint32_t mWidth;
    uint32_t mHeight;
    uint32_t mFarOrientationDegree;
    uint32_t mNearOrientationDegree;
    bool mStopped;
};

#endif  // IMSMEDIA_VIDEO_RENDERER_H_INCLUDED
