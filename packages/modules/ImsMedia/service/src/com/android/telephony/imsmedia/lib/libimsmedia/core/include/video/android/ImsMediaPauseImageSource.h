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

#ifndef IMSMEDIA_JPEG_SOURCE_H_INCLUDED
#define IMSMEDIA_JPEG_SOURCE_H_INCLUDED

#include <android/asset_manager_jni.h>

class ImsMediaPauseImageSource
{
public:
    ImsMediaPauseImageSource();
    ~ImsMediaPauseImageSource();

    /**
     * @brief Based on the video resolution ( width x height ), an image asset with same
     * resolution is loaded and converted to YUV format.
     *
     * @param width width of the video frames.
     * @param height height of the video frames.
     * @param stride stride of the video frames.
     *
     * @return returns true if the image is loaded and false in case of failure.
     */
    bool Initialize(int width, int height, int stride);

    /**
     * @brief Image YUV buffer loaded in memory is freed.
     */
    void Uninitialize();

    /**
     * @brief Returns the Image buffer in YUV format.
     *
     * @param buffer Image buffer is copied to this buffer.
     * @param len length of the input buffer.
     *
     * @return size of the image YUV data copied to input buffer.
     */
    size_t GetYuvImage(uint8_t* buffer, size_t len);

private:
    int mWidth, mHeight;
    int8_t* mYuvImageBuffer;
    size_t mBufferSize;

    AAsset* getImageAsset();
    const char* getImageFilePath();
    int8_t* ConvertRgbaToYuv(int8_t* pixels, int width, int height, int stride);
};

#endif  // IMSMEDIA_JPEG_SOURCE_H_INCLUDED
