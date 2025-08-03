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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android-base/unique_fd.h>
#include <android/imagedecoder.h>
#include <ImsMediaTrace.h>
#include "ImsMediaPauseImageSource.h"

// TODO: Pause images from Irvine source are used. Get new pause images from UX team and replace.
// TODO: Write unit test cases for this class
#define DEFAULT_FHD_PORTRAIT_PAUSE_IMG_PATH    "pause_images/pause_img_fhd_p.jpg"
#define DEFAULT_FHD_LANDSCAPE_PAUSE_IMG_PATH   "pause_images/pause_img_fhd_l.jpg"
#define DEFAULT_HD_PORTRAIT_PAUSE_IMG_PATH     "pause_images/pause_img_hd_p.jpg"
#define DEFAULT_HD_LANDSCAPE_PAUSE_IMG_PATH    "pause_images/pause_img_hd_l.jpg"
#define DEFAULT_VGA_PORTRAIT_PAUSE_IMG_PATH    "pause_images/pause_img_vga_p.jpg"
#define DEFAULT_VGA_LANDSCAPE_PAUSE_IMG_PATH   "pause_images/pause_img_vga_l.jpg"
#define DEFAULT_QVGA_PORTRAIT_PAUSE_IMG_PATH   "pause_images/pause_img_qvga_p.jpg"
#define DEFAULT_QVGA_LANDSCAPE_PAUSE_IMG_PATH  "pause_images/pause_img_qvga_l.jpg"
#define DEFAULT_CIF_PORTRAIT_PAUSE_IMG_PATH    "pause_images/pause_img_cif_p.jpg"
#define DEFAULT_CIF_LANDSCAPE_PAUSE_IMG_PATH   "pause_images/pause_img_cif_l.jpg"
#define DEFAULT_QCIF_PORTRAIT_PAUSE_IMG_PATH   "pause_images/pause_img_qcif_p.jpg"
#define DEFAULT_QCIF_LANDSCAPE_PAUSE_IMG_PATH  "pause_images/pause_img_qcif_l.jpg"
#define DEFAULT_SIF_PORTRAIT_PAUSE_IMG_PATH    "pause_images/pause_img_sif_p.jpg"
#define DEFAULT_SIF_LANDSCAPE_PAUSE_IMG_PATH   "pause_images/pause_img_sif_l.jpg"
#define DEFAULT_SQCIF_PORTRAIT_PAUSE_IMG_PATH  "pause_images/pause_img_sqcif_p.jpg"
#define DEFAULT_SQCIF_LANDSCAPE_PAUSE_IMG_PATH "pause_images/pause_img_sqcif_l.jpg"

extern AAssetManager* gpAssetManager;

ImsMediaPauseImageSource::ImsMediaPauseImageSource()
{
    mYuvImageBuffer = nullptr;
    mBufferSize = 0;
}

ImsMediaPauseImageSource::~ImsMediaPauseImageSource()
{
    Uninitialize();
}

void ImsMediaPauseImageSource::Uninitialize()
{
    if (mYuvImageBuffer != nullptr)
    {
        free(mYuvImageBuffer);
        mYuvImageBuffer = nullptr;
    }
}

bool ImsMediaPauseImageSource::Initialize(int width, int height, int stride)
{
    IMLOGD3("[ImsMediaPauseImageSource] Init(width:%d, height:%d, stride:%d)", width, height,
            stride);
    mWidth = width;
    mHeight = height;

    // Decode JPEG image and save in YUV buffer.
    AAsset* asset = getImageAsset();
    if (asset == nullptr)
    {
        IMLOGE0("[ImsMediaPauseImageSource] Failed to open pause image");
        return false;
    }

    AImageDecoder* decoder;
    int result = AImageDecoder_createFromAAsset(asset, &decoder);
    if (result != ANDROID_IMAGE_DECODER_SUCCESS)
    {
        IMLOGE0("[ImsMediaPauseImageSource] Failed to decode pause image");
        return false;
    }

    const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(decoder);
    int32_t JpegWidth = AImageDecoderHeaderInfo_getWidth(info);
    int32_t JpegHeight = AImageDecoderHeaderInfo_getHeight(info);
    if (JpegWidth != mWidth || JpegHeight != mHeight)
    {
        IMLOGE0("[ImsMediaPauseImageSource] Decoded image resolution doesn't match with JPEG image"
                "resolution");
        AImageDecoder_delete(decoder);
        AAsset_close(asset);
        return false;
    }

    /*
     * TODO: AImageDecoder output should be in ANDROID_BITMAP_FORMAT_RGBA_8888 format.
     *       If not, configure AImageDecoder accordingly.
     *
     * AndroidBitmapFormat format =
     *      (AndroidBitmapFormat)AImageDecoderHeaderInfo_getAndroidBitmapFormat(info);
     */

    size_t decStride = AImageDecoder_getMinimumStride(decoder);  // Image decoder does not
    // use padding by default
    size_t size = height * decStride;
    int8_t* pixels = reinterpret_cast<int8_t*>(malloc(size));

    result = AImageDecoder_decodeImage(decoder, pixels, decStride, size);
    if (result != ANDROID_IMAGE_DECODER_SUCCESS)
    {
        IMLOGE0("[ImsMediaPauseImageSource] error occurred, and the file could not be decoded.");
        AImageDecoder_delete(decoder);
        AAsset_close(asset);
        return false;
    }

    mYuvImageBuffer = ConvertRgbaToYuv(pixels, width, height, stride);

    AImageDecoder_delete(decoder);
    free(pixels);
    AAsset_close(asset);
    return true;
}

size_t ImsMediaPauseImageSource::GetYuvImage(uint8_t* buffer, size_t len)
{
    if (buffer == nullptr)
    {
        IMLOGE0("[ImsMediaPauseImageSource] GetYuvImage. buffer == nullptr");
        return 0;
    }

    if (len >= mBufferSize)
    {
        memcpy(buffer, mYuvImageBuffer, mBufferSize);
        return mBufferSize;
    }

    IMLOGE2("[ImsMediaPauseImageSource] buffer size is smaller. Expected Bufsize[%d], passed[%d]",
            mBufferSize, len);
    return 0;
}

AAsset* ImsMediaPauseImageSource::getImageAsset()
{
    IMLOGD0("[ImsMediaPauseImageSource] getImageFileFd");
    if (gpAssetManager == nullptr)
    {
        IMLOGE0("[ImsMediaPauseImageSource] AssetManager is nullptr");
        return nullptr;
    }

    const char* filePath = getImageFilePath();
    return AAssetManager_open(gpAssetManager, filePath, AASSET_MODE_RANDOM);
}

const char* ImsMediaPauseImageSource::getImageFilePath()
{
    if (mWidth == 1920 && mHeight == 1080)
        return DEFAULT_FHD_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 1080 && mHeight == 1920)
        return DEFAULT_FHD_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 1280 && mHeight == 720)
        return DEFAULT_HD_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 720 && mHeight == 1280)
        return DEFAULT_HD_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 640 && mHeight == 480)
        return DEFAULT_VGA_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 480 && mHeight == 640)
        return DEFAULT_VGA_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 352 && mHeight == 288)
        return DEFAULT_CIF_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 288 && mHeight == 352)
        return DEFAULT_CIF_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 352 && mHeight == 240)
        return DEFAULT_SIF_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 240 && mHeight == 352)
        return DEFAULT_SIF_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 320 && mHeight == 240)
        return DEFAULT_QVGA_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 240 && mHeight == 320)
        return DEFAULT_QVGA_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 176 && mHeight == 144)
        return DEFAULT_QCIF_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 144 && mHeight == 176)
        return DEFAULT_QCIF_PORTRAIT_PAUSE_IMG_PATH;
    else if (mWidth == 128 && mHeight == 96)
        return DEFAULT_SQCIF_LANDSCAPE_PAUSE_IMG_PATH;
    else if (mWidth == 96 && mHeight == 128)
        return DEFAULT_SQCIF_PORTRAIT_PAUSE_IMG_PATH;
    else
    {
        IMLOGE2("Resolution [%dx%d] pause image is not available", mWidth, mHeight);
    }

    return nullptr;
}

int8_t* ImsMediaPauseImageSource::ConvertRgbaToYuv(
        int8_t* pixels, int width, int height, int stride)
{
    // src array must be integer array, data have no padding alignment
    int32_t* pSrcArray = reinterpret_cast<int32_t*>(pixels);
    mBufferSize = stride * height * 1.5;
    int8_t* pDstArray = reinterpret_cast<int8_t*>(malloc(mBufferSize));
    int32_t nYIndex = 0;
    int32_t nUVIndex = stride * height;
    int32_t r, g, b, padLen = stride - width;
    double y, u, v;

    for (int32_t j = 0; j < height; j++)
    {
        int32_t nIndex = width * j;
        for (int32_t i = 0; i < width; i++)
        {
            r = (pSrcArray[nIndex] & 0xff0000) >> 16;
            g = (pSrcArray[nIndex] & 0xff00) >> 8;
            b = (pSrcArray[nIndex] & 0xff) >> 0;
            nIndex++;

            // rgb to yuv
            y = 0.257 * r + 0.504 * g + 0.098 * b + 16;
            u = 128 + 0.439 * r - 0.368 * g - 0.071 * b;
            v = 128 - 0.148 * r - 0.291 * g + 0.439 * b;

            // clip y
            pDstArray[nYIndex++] = (uint8_t)((y < 0) ? 0 : ((y > 255) ? 255 : y));

            if (j % 2 == 0 && nIndex % 2 == 0)
            {
                pDstArray[nUVIndex++] = (uint8_t)((v < 0) ? 0 : ((v > 255) ? 255 : v));
                pDstArray[nUVIndex++] = (uint8_t)((u < 0) ? 0 : ((u > 255) ? 255 : u));
            }
        }

        // Add padding if stride > width
        if (padLen > 0)
        {
            nYIndex += padLen;

            if (j % 2 == 0)
            {
                nUVIndex += padLen;
            }
        }
    }

    mBufferSize -= padLen;
    return pDstArray;
}
