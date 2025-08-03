/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __HARDWARE_EXYNOS_JPEG_ENCODER_FOR_CAMERA_H__
#define __HARDWARE_EXYNOS_JPEG_ENCODER_FOR_CAMERA_H__

#include <ExynosExif.h>
#include <hardware/exynos/ExynosExif.h>
#include <pthread.h>

#include <memory>

#include "ExynosJpegApi.h"

class CAppMarkerWriter; // defined in libhwjpeg/AppMarkerWriter.h
class ThumbnailScaler;  // defined in libhwjpeg/thumbnail_scaler.h

class ExynosJpegEncoderForCamera : public ExynosJpegEncoder {
    enum {
        STATE_THUMBSIZE_CHANGED = STATE_BASE_MAX << 0,
        STATE_HWFC_ENABLED = STATE_BASE_MAX << 1,
        STATE_NO_CREATE_THUMBIMAGE = STATE_BASE_MAX << 2,
        STATE_NO_BTBCOMP = STATE_BASE_MAX << 3,
    };

    CHWJpegCompressor *m_phwjpeg4thumb;
    std::unique_ptr<ThumbnailScaler> mThumbnailScaler;
    int m_fdIONClient;
    int m_fdIONThumbImgBuffer;
    char *m_pIONThumbImgBuffer;
    size_t m_szIONThumbImgBuffer;
    char *m_pIONThumbJpegBuffer;
    int m_fdIONThumbJpegBuffer;
    size_t m_szIONThumbJpegBuffer;

    int m_nThumbWidth;
    int m_nThumbHeight;
    int m_nThumbQuality;

    int m_iHWScalerID;

    /*
     * The following four placeholders and size vairables are used
     * by asynchronous(non-blocking) compression
     */
    char *m_pStreamBase;
    size_t m_nStreamSize;

    char m_fThumbBufferType;

    union {
        char *m_pThumbnailImageBuffer[3]; // checkInBufType() == JPEG_BUF_TYPE_USER_PTR
        int m_fdThumbnailImageBuffer[3];  // checkInBufType() == JPEG_BUF_TYPE_DMA_BUF
    };
    size_t m_szThumbnailImageLen[3];

    CAppMarkerWriter *m_pAppWriter;

    pthread_t m_threadWorker;

    extra_appinfo_t m_extraInfo;
    app_info_t m_appInfo[15];

    bool AllocThumbBuffer(int v4l2Format); /* For single compression */
    bool AllocThumbJpegBuffer();           /* For BTB compression */
    bool GenerateThumbnailImage();
    size_t CompressThumbnail();
    size_t CompressThumbnailOnly(size_t limit, int quality, unsigned int v4l2Format,
                                 int src_buftype);
    size_t RemoveTrailingDummies(char *base, size_t len);
    ssize_t FinishCompression(size_t mainlen, size_t thumblen);
    bool ProcessExif(char *base, size_t limit, exif_attribute_t *exifInfo, extra_appinfo_t *extra);
    static void *tCompressThumbnail(void *p);
    bool PrepareCompression(bool thumbnail);

    // IsThumbGenerationNeeded - true if thumbnail image needed to be generated from the main image
    //                           It also implies that a worker thread is generated to generate
    //                           thumbnail concurrently.
    inline bool IsThumbGenerationNeeded() { return !TestState(STATE_NO_CREATE_THUMBIMAGE); }
    inline void NoThumbGenerationNeeded() { SetState(STATE_NO_CREATE_THUMBIMAGE); }
    inline void ThumbGenerationNeeded() { ClearState(STATE_NO_CREATE_THUMBIMAGE); }

    inline bool IsBTBCompressionSupported() {
        return !!(GetDeviceCapabilities() & V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION) &&
                !TestState(STATE_NO_BTBCOMP);
    }

protected:
    virtual bool EnsureFormatIsApplied();

public:
    ExynosJpegEncoderForCamera(bool bBTBComp = true);
    virtual ~ExynosJpegEncoderForCamera();

    int encode(int *size, exif_attribute_t *exifInfo, char **pcJpegBuffer,
               debug_attribute_t *debugInfo = 0);
    int encode(int *size, exif_attribute_t *exifInfo, int fdJpegBuffer, char **pcJpegBuffer,
               debug_attribute_t *debugInfo = 0);
    int encode(int *size, exif_attribute_t *exifInfo, int fdJpegBuffer, char **pcJpegBuffer,
               extra_appinfo_t *appInfo = 0);
    int setInBuf2(int *piBuf, int *iSize);
    int setInBuf2(char **pcBuf, int *iSize);
    int setThumbnailSize(int w, int h);
    int setThumbnailQuality(int quality);
    int setThumbnailPadding(const unsigned char *padding, unsigned int num_planes);

    void setExtScalerNum(int csc_hwscaler_id) { m_iHWScalerID = csc_hwscaler_id; }

    void EnableHWFC() {
        SetState(STATE_HWFC_ENABLED);
        GetCompressor().SetAuxFlags(EXYNOS_HWJPEG_AUXOPT_ENABLE_HWFC);
    }
    void DisableHWFC() {
        GetCompressor().ClearAuxFlags(EXYNOS_HWJPEG_AUXOPT_ENABLE_HWFC);
        ClearState(STATE_HWFC_ENABLED);
    }

    ssize_t WaitForCompression();

    size_t GetThumbnailImage(char *buffer, size_t buflen);

    virtual int destroy(void);
};

#endif //__HARDWARE_EXYNOS_JPEG_ENCODER_FOR_CAMERA_H__
