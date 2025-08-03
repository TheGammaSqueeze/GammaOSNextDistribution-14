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

#ifndef __HARDWARE_EXYNOS_EXYNOS_JPEG_API_H__
#define __HARDWARE_EXYNOS_EXYNOS_JPEG_API_H__

// Exynos HAL defines another version of videodev2 apart from the original one
// This prevents conflict from the Exynos HAL from the original videodev2.h
/*
#ifndef v4l2_fourcc
#include <linux/videodev2.h>
#endif
*/

#include <exynos-hwjpeg.h>

#ifndef JPEG_CACHE_ON
#define JPEG_CACHE_ON 1
#endif

#define JPEG_BUF_TYPE_USER_PTR 1
#define JPEG_BUF_TYPE_DMA_BUF 2

// CUSTOM V4L2 4CC FORMATS FOR LEGACY JPEG LIBRARY AND DRIVERS
#ifndef V4L2_PIX_FMT_JPEG_444
#define V4L2_PIX_FMT_JPEG_444 v4l2_fourcc('J', 'P', 'G', '4')
#endif
#ifndef V4L2_PIX_FMT_JPEG_422
#define V4L2_PIX_FMT_JPEG_422 v4l2_fourcc('J', 'P', 'G', '2')
#endif
#ifndef V4L2_PIX_FMT_JPEG_420
#define V4L2_PIX_FMT_JPEG_420 v4l2_fourcc('J', 'P', 'G', '0')
#endif
#ifndef V4L2_PIX_FMT_JPEG_GRAY
#define V4L2_PIX_FMT_JPEG_GRAY v4l2_fourcc('J', 'P', 'G', 'G')
#endif
#ifndef V4L2_PIX_FMT_JPEG_422V
#define V4L2_PIX_FMT_JPEG_422V v4l2_fourcc('J', 'P', 'G', '5')
#endif
#ifndef V4L2_PIX_FMT_JPEG_411
#define V4L2_PIX_FMT_JPEG_411 v4l2_fourcc('J', 'P', 'G', '1')
#endif

class ExynosJpegEncoder {
    /*
     * ExynosJpedgEncoder class is actually a derived class of
     * CHWJpegV4L2Compressor. But it is not derived from CHWJpegV4L2Compressor
     * because it has a lot of virtual functions which require extra memory for
     * vtables. Moreover, ExynosJpegEncoder class implements no virtual function
     * of CHWJpegV4L2Compressor.
     */
    CHWJpegV4L2Compressor m_hwjpeg;

    char m_iInBufType;
    char m_iOutBufType;

    unsigned int m_uiState;

    int m_nQFactor;
    int m_nWidth;
    int m_nHeight;
    int m_v4l2Format;
    int m_jpegFormat;
    int m_nStreamSize;

    bool __EnsureFormatIsApplied();

protected:
    enum {
        STATE_SIZE_CHANGED = 1 << 0,
        STATE_PIXFMT_CHANGED = 1 << 1,
        STATE_BASE_MAX = 1 << 16,
    };

    unsigned int GetDeviceCapabilities() { return m_hwjpeg.GetDeviceCapabilities(); }
    CHWJpegCompressor &GetCompressor() { return m_hwjpeg; }
    unsigned int GetHWDelay() { return m_hwjpeg.GetHWDelay(); }

    void SetState(unsigned int state) { m_uiState |= state; }
    void ClearState(unsigned int state) { m_uiState &= ~state; }
    bool TestState(unsigned int state) { return (m_uiState & state) == state; }
    bool TestStateEither(unsigned int state) { return (m_uiState & state) != 0; }

    virtual bool EnsureFormatIsApplied() { return __EnsureFormatIsApplied(); }

public:
    ExynosJpegEncoder()
          : m_hwjpeg(),
            m_iInBufType(JPEG_BUF_TYPE_USER_PTR),
            m_iOutBufType(JPEG_BUF_TYPE_USER_PTR),
            m_uiState(0),
            m_nQFactor(0),
            m_nWidth(0),
            m_nHeight(0),
            m_v4l2Format(0),
            m_jpegFormat(0),
            m_nStreamSize(0) {
        /* To detect setInBuf() call without format setting */
        SetState(STATE_SIZE_CHANGED | STATE_PIXFMT_CHANGED);
    }
    virtual ~ExynosJpegEncoder() { destroy(); }

    // Acquire exclusive lock to V4L2 device. This is a blocking call.
    int lock();
    // Release exclusive lock to V4L2 device.
    int unlock();

    // Return 0 on success, -1 on error
    int flagCreate() { return m_hwjpeg.Okay() ? 0 : -1; }
    virtual int create(void) { return flagCreate(); }
    virtual int destroy(void) { return 0; }
    int updateConfig(void) { return 0; }
    int setCache(int __unused val) { return 0; }

    void *getJpegConfig() { return reinterpret_cast<void *>(this); }
    int setJpegConfig(void *pConfig);

    int checkInBufType(void) { return m_iInBufType; }
    int checkOutBufType(void) { return m_iOutBufType; }

    int getInBuf(int *piBuf, int *piInputSize, int iSize);
    int getOutBuf(int *piBuf, int *piOutputSize);
    int getInBuf(char **pcBuf, int *piInputSize, int iSize);
    int getOutBuf(char **pcBuf, int *piOutputSize);

    int setInBuf(int *piBuf, int *iSize);
    int setOutBuf(int iBuf, int iSize, int offset = 0);
    int setInBuf(char **pcBuf, int *iSize);
    int setOutBuf(char *pcBuf, int iSize);

    int getSize(int *piWidth, int *piHeight) {
        *piWidth = m_nWidth;
        *piHeight = m_nHeight;
        return 0;
    }

    int setSize(int iW, int iH) {
        if ((m_nWidth != iW) || (m_nHeight != iH)) {
            m_nWidth = iW;
            m_nHeight = iH;
            SetState(STATE_SIZE_CHANGED);
        }
        return 0;
    }

    int setJpegFormat(int iV4l2JpegFormat);
    int getColorFormat(void) { return m_v4l2Format; }
    int setColorFormat(int iV4l2ColorFormat) {
        if (iV4l2ColorFormat != m_v4l2Format) {
            m_v4l2Format = iV4l2ColorFormat;
            SetState(STATE_PIXFMT_CHANGED);
        }
        return 0;
    }

    int setQuality(int iQuality) {
        if (m_nQFactor != iQuality) {
            if (!m_hwjpeg.SetQuality(static_cast<unsigned int>(iQuality))) return -1;
            m_nQFactor = iQuality;
        }
        return 0;
    }

    int setQuality(const unsigned char q_table[]);
    int setPadding(const unsigned char *padding, unsigned int num_planes);

    int setColorBufSize(int *piBufSize, int iSize);
    int getJpegSize(void) { return m_nStreamSize; }

    int encode(void) {
        if (!__EnsureFormatIsApplied()) return false;

        m_nStreamSize = static_cast<int>(m_hwjpeg.Compress());
        return (m_nStreamSize < 0) ? -1 : 0;
    }
};

#endif //__HARDWARE_EXYNOS_EXYNOS_JPEG_API_H__
