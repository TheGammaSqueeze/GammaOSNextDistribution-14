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

#ifndef IMSMEDIA_VIDEO_SOURCE_H_INCLUDED
#define IMSMEDIA_VIDEO_SOURCE_H_INCLUDED

#include <ImsMediaVideoUtil.h>
#include <ImsMediaDefine.h>
#include <android/native_window.h>
#include <ImsMediaCamera.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkImageReader.h>
#include <ImsMediaCondition.h>
#include "ImsMediaPauseImageSource.h"

class IVideoSourceCallback
{
public:
    virtual ~IVideoSourceCallback() {}
    virtual void OnUplinkEvent(
            uint8_t* pBitstream, uint32_t nSize, int64_t pstUsec, uint32_t flag) = 0;
    virtual void OnEvent(int32_t type, int32_t param1, int32_t param2) = 0;
};

enum kImsMediaVideoMode
{
    kVideoModePreview = 0,
    kVideoModeRecording,
    kVideoModePauseImage,
};

enum kVideoSourceEvent
{
    kVideoSourceEventUpdateOrientation = 0,
    kVideoSourceEventCameraError,
};

/**
 * @brief
 *
 */
class ImsMediaVideoSource
{
public:
    ImsMediaVideoSource();
    ~ImsMediaVideoSource();

    /**
     * @brief Set the IVideoSourceCallback object listener
     */
    void SetListener(IVideoSourceCallback* listener);

    /**
     * @brief Set the VideoMode defined in VideoConfig
     */
    void SetVideoMode(const int32_t mode);

    /**
     * @brief Set the Camera configuration parameter, it should set before open camera
     *
     * @param cameraId The camera device id
     * @param cameraZoom The camera zoom level
     */
    void SetCameraConfig(const uint32_t cameraId, const uint32_t cameraZoom);

    /**
     * @brief Set the pause image path stored
     */
    void SetImagePath(const android::String8& path);

    /**
     * @brief Set the Codec configuration parameter, this method should be called before calling
     * start method of the ImsMediaVideoSource instance
     *
     * @param codecType The codec type to run, Avc and HEVC codec is available
     * @param profile The codec profile
     * @param level The codec level
     * @param bitrate The bitrate of encoding frames in kbps units
     * @param framerate The framerate of the encoding frames
     * @param interval The interval of the idr frames
     */
    void SetCodecConfig(const int32_t codecType, const uint32_t profile, const uint32_t level,
            const uint32_t bitrate, const uint32_t framerate, const uint32_t interval);

    /**
     * @brief Set the resolution of encoded output frames required
     */
    void SetResolution(const uint32_t width, const uint32_t height);

    /**
     * @brief Set the surface buffer for preview surface view
     */
    void SetSurface(ANativeWindow* window);

    /**
     * @brief Set the device orientation value
     *
     * @param degree The orientation in degree units
     */
    void SetDeviceOrientation(const uint32_t degree);

    /** Start the acquires raw frame from the source and encode or sends it to the preview surface
     * buffer based on the VideoMode configured*/
    bool Start();

    /** Stop the image flow */
    void Stop();

    /**
     * @brief Get the video source node is stopped
     */
    bool IsStopped();

    /**
     * @brief Called when the valid input camera frame is ready.
     *
     * @param pImage The camera frame object
     */
    void onCameraFrame(AImage* pImage);

    /**
     * @brief Change bitrate of the encoding frames
     *
     * @param bitrate The bitrate in bps units
     */
    bool changeBitrate(const uint32_t bitrate);

    /**
     * @brief Request a new IDR frame to the codec output streaming
     */
    void requestIdrFrame();

private:
    void EncodePauseImage();
    void processOutputBuffer();
    ANativeWindow* CreateImageReader(int width, int height);

    ImsMediaCamera* mCamera;
    ANativeWindow* mWindow;
    AMediaCodec* mCodec;
    AMediaFormat* mFormat;
    ANativeWindow* mImageReaderSurface;
    AImageReader* mImageReader;
    std::mutex mMutex;
    ImsMediaCondition mConditionExit;
    IVideoSourceCallback* mListener;
    ImsMediaPauseImageSource mPauseImageSource;
    int32_t mCodecType;
    int32_t mVideoMode;
    uint32_t mCodecProfile;
    uint32_t mCodecLevel;
    uint32_t mCameraId;
    uint32_t mCameraZoom;
    uint32_t mWidth;
    uint32_t mHeight;
    int32_t mCodecStride;
    uint32_t mFramerate;
    uint32_t mBitrate;
    uint32_t mIntraInterval;
    android::String8 mImagePath;
    int32_t mDeviceOrientation;
    uint64_t mTimestamp;
    uint64_t mPrevTimestamp;
    bool mStopped;
};
#endif