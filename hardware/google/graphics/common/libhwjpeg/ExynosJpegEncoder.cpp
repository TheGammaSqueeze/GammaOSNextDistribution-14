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

#include <ExynosJpegApi.h>
#include <linux/videodev2.h>

#include "hwjpeg-internal.h"

int ExynosJpegEncoder::lock() {
    return m_hwjpeg.lock();
}

int ExynosJpegEncoder::unlock() {
    return m_hwjpeg.unlock();
}

int ExynosJpegEncoder::setJpegConfig(void *pConfig) {
    ExynosJpegEncoder *that = reinterpret_cast<ExynosJpegEncoder *>(pConfig);

    if (!setColorFormat(that->m_v4l2Format)) return -1;

    if (!setJpegFormat(that->m_jpegFormat)) return -1;

    if (!setSize(that->m_nWidth, that->m_nHeight)) return -1;

    m_iInBufType = that->m_iInBufType;
    m_iOutBufType = that->m_iOutBufType;

    return 0;
}

int ExynosJpegEncoder::getInBuf(int *piBuf, int *piInputSize, int iSize) {
    if (iSize < 1) {
        ALOGE("Invalid array size %d for getInBuf()", iSize);
        return -1;
    }

    size_t len_buffers[iSize];
    if (!m_hwjpeg.GetImageBuffers(piBuf, len_buffers, static_cast<unsigned int>(iSize))) return -1;

    for (int i = 0; i < iSize; i++) piInputSize[i] = static_cast<int>(len_buffers[i]);

    return 0;
}

int ExynosJpegEncoder::getOutBuf(int *piBuf, int *piOutputSize) {
    size_t len;
    if (!m_hwjpeg.GetJpegBuffer(piBuf, &len)) return -1;

    *piOutputSize = static_cast<int>(len);
    return 0;
}

int ExynosJpegEncoder::setInBuf(int *piBuf, int *iSize) {
    size_t buflen[3];
    unsigned int bufnum = 3;

    if (!EnsureFormatIsApplied()) return -1;

    if (!m_hwjpeg.GetImageBufferSizes(buflen, &bufnum)) return -1;

    for (unsigned int i = 0; i < bufnum; i++) buflen[i] = static_cast<size_t>(iSize[i]);

    if (!m_hwjpeg.SetImageBuffer(piBuf, buflen, bufnum)) return -1;

    m_iInBufType = JPEG_BUF_TYPE_DMA_BUF;

    return 0;
}

int ExynosJpegEncoder::setOutBuf(int iBuf, int iSize, int offset) {
    if (!m_hwjpeg.SetJpegBuffer(iBuf, static_cast<size_t>(iSize), offset)) return -1;

    m_iOutBufType = JPEG_BUF_TYPE_DMA_BUF;

    return 0;
}

int ExynosJpegEncoder::getInBuf(char **pcBuf, int *piInputSize, int iSize) {
    if (iSize < 1) {
        ALOGE("Invalid array size %d for getInBuf()", iSize);
        return -1;
    }

    size_t len_buffers[iSize];
    if (!m_hwjpeg.GetImageBuffers(pcBuf, len_buffers, static_cast<unsigned int>(iSize))) return -1;

    for (int i = 0; i < iSize; i++) piInputSize[i] = static_cast<int>(len_buffers[i]);

    return 0;
}

int ExynosJpegEncoder::getOutBuf(char **pcBuf, int *piOutputSize) {
    size_t len;
    if (!m_hwjpeg.GetJpegBuffer(pcBuf, &len)) return -1;

    *piOutputSize = static_cast<int>(len);
    return 0;
}

int ExynosJpegEncoder::setInBuf(char **pcBuf, int *iSize) {
    size_t buflen[3];
    unsigned int bufnum = 3;

    if (!EnsureFormatIsApplied()) return -1;

    if (!m_hwjpeg.GetImageBufferSizes(buflen, &bufnum)) return -1;

    for (unsigned int i = 0; i < bufnum; i++) buflen[i] = static_cast<size_t>(iSize[i]);

    if (!m_hwjpeg.SetImageBuffer(pcBuf, buflen, bufnum)) return -1;

    m_iInBufType = JPEG_BUF_TYPE_USER_PTR;
    return 0;
}

int ExynosJpegEncoder::setOutBuf(char *pcBuf, int iSize) {
    if (!m_hwjpeg.SetJpegBuffer(pcBuf, static_cast<size_t>(iSize))) return -1;

    m_iOutBufType = JPEG_BUF_TYPE_USER_PTR;

    return 0;
}

int ExynosJpegEncoder::setJpegFormat(int iV4l2JpegFormat) {
    if (m_jpegFormat == iV4l2JpegFormat) return 0;

    unsigned int hfactor, vfactor;
    switch (iV4l2JpegFormat) {
        case V4L2_PIX_FMT_JPEG_444:
            hfactor = 1;
            vfactor = 1;
            break;
        case V4L2_PIX_FMT_JPEG_422:
            hfactor = 2;
            vfactor = 1;
            break;
        case V4L2_PIX_FMT_JPEG_420:
            hfactor = 2;
            vfactor = 2;
            break;
        case V4L2_PIX_FMT_JPEG_GRAY:
            hfactor = 0;
            vfactor = 0;
            break;
        case V4L2_PIX_FMT_JPEG_422V:
            hfactor = 1;
            vfactor = 2;
            break;
        case V4L2_PIX_FMT_JPEG_411:
            hfactor = 4;
            vfactor = 1;
            break;
        default:
            ALOGE("Unknown JPEG format `%08Xh", iV4l2JpegFormat);
            return -1;
    }

    if (!m_hwjpeg.SetChromaSampFactor(hfactor, vfactor)) return -1;

    m_jpegFormat = iV4l2JpegFormat;

    return 0;
}

int ExynosJpegEncoder::setColorBufSize(int *piBufSize, int iSize) {
    size_t len[3];
    unsigned int num = static_cast<unsigned int>(iSize);

    if (!m_hwjpeg.GetImageBufferSizes(len, &num)) return -1;

    for (unsigned int i = 0; i < num; i++) piBufSize[i] = static_cast<int>(len[i]);

    return 0;
}

bool ExynosJpegEncoder::__EnsureFormatIsApplied() {
    if (TestStateEither(STATE_SIZE_CHANGED | STATE_PIXFMT_CHANGED) &&
        !m_hwjpeg.SetImageFormat(m_v4l2Format, m_nWidth, m_nHeight))
        return false;

    ClearState(STATE_SIZE_CHANGED | STATE_PIXFMT_CHANGED);
    return true;
}

int ExynosJpegEncoder::setQuality(const unsigned char q_table[]) {
    return m_hwjpeg.SetQuality(q_table) ? 0 : -1;
}

int ExynosJpegEncoder::setPadding(const unsigned char *padding, unsigned int num_planes) {
    return m_hwjpeg.SetPadding(padding, num_planes) ? 0 : -1;
}
