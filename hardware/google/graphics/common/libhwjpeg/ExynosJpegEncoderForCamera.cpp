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

#include <ExynosJpegEncoderForCamera.h>
#include <hardware/exynos/ion.h>
#include <linux/videodev2.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <system/graphics.h>

#include "AppMarkerWriter.h"
#include "ThumbnailScaler.h"
#include "hwjpeg-internal.h"

// Data length written by H/W without the scan data.
#define NECESSARY_JPEG_LENGTH (0x24B + 2 * JPEG_MARKER_SIZE)

static size_t GetImageLength(unsigned int width, unsigned int height, int v4l2Format) {
    size_t size = width * height;

    switch (v4l2Format) {
        case V4L2_PIX_FMT_YUYV:
        case V4L2_PIX_FMT_YVYU:
        case V4L2_PIX_FMT_UYVY:
        case V4L2_PIX_FMT_VYUY:
        case V4L2_PIX_FMT_NV16:
        case V4L2_PIX_FMT_NV61:
        case V4L2_PIX_FMT_YUV422P:
            return size * 2;
        case V4L2_PIX_FMT_NV12:
        case V4L2_PIX_FMT_NV21:
        case V4L2_PIX_FMT_NV12M:
        case V4L2_PIX_FMT_NV21M:
        case V4L2_PIX_FMT_YUV420:
            return size + (size / 4) * 2;
    }

    return 0;
}

static int GetThumbnailFormat(int v4l2Format) {
    if (v4l2Format == V4L2_PIX_FMT_NV12M)
        return V4L2_PIX_FMT_NV12;
    else if (v4l2Format == V4L2_PIX_FMT_NV21M)
        return V4L2_PIX_FMT_NV21;
    else
        return v4l2Format;
}

ExynosJpegEncoderForCamera::ExynosJpegEncoderForCamera(bool bBTBComp)
      : m_phwjpeg4thumb(NULL),
        m_fdIONClient(-1),
        m_fdIONThumbImgBuffer(-1),
        m_pIONThumbImgBuffer(NULL),
        m_szIONThumbImgBuffer(0),
        m_pIONThumbJpegBuffer(NULL),
        m_fdIONThumbJpegBuffer(-1),
        m_szIONThumbJpegBuffer(0),
        m_nThumbWidth(0),
        m_nThumbHeight(0),
        m_nThumbQuality(0),
        m_pStreamBase(NULL),
        m_fThumbBufferType(0) {
    m_pAppWriter = new CAppMarkerWriter();
    if (!m_pAppWriter) {
        ALOGE("Failed to allocated an instance of CAppMarkerWriter");
        return;
    }

    m_phwjpeg4thumb = new CHWJpegV4L2Compressor();
    if (!m_phwjpeg4thumb) {
        ALOGE("Failed to create thumbnail compressor!");
        return;
    }

    if (!m_phwjpeg4thumb->SetChromaSampFactor(2, 2)) {
        ALOGE("Failed to configure chroma subsampling factor to YUV420 for thumbnail compression");
    }

    m_fdIONClient = exynos_ion_open();
    if (m_fdIONClient < 0) {
        ALOGERR("Failed to create ION client for thumbnail conversion");
    }

    if (!bBTBComp) SetState(STATE_NO_BTBCOMP);

    // STATE_THUMBSIZE_CHANGED is to know if thumbnail image size need to be
    // configured to HWJPEG. If HWJPEG does not support for back-to-back
    // compression, it should not be configured.
    if (IsBTBCompressionSupported()) SetState(STATE_THUMBSIZE_CHANGED);

    m_extraInfo.appInfo = m_appInfo;

    mThumbnailScaler.reset(ThumbnailScaler::createInstance());
    if (!mThumbnailScaler->available()) ALOGW("Thumbnail scaler is not available.");

    ALOGD("ExynosJpegEncoderForCamera Created: %p, ION %d", this, m_fdIONClient);
}

ExynosJpegEncoderForCamera::~ExynosJpegEncoderForCamera() {
    delete m_pAppWriter;
    delete m_phwjpeg4thumb;

    if (m_pIONThumbImgBuffer != NULL) munmap(m_pIONThumbImgBuffer, m_szIONThumbImgBuffer);

    if (m_fdIONThumbImgBuffer >= 0) close(m_fdIONThumbImgBuffer);

    if (m_pIONThumbJpegBuffer) munmap(m_pIONThumbJpegBuffer, m_szIONThumbJpegBuffer);

    if (m_fdIONThumbJpegBuffer >= 0) close(m_fdIONThumbJpegBuffer);

    if (m_fdIONClient >= 0) exynos_ion_close(m_fdIONClient);

    ALOGD("ExynosJpegEncoderForCamera Destroyed: %p, ION %d, ThumIMG %d", this, m_fdIONClient,
          m_fdIONThumbImgBuffer);
}

int ExynosJpegEncoderForCamera::setThumbnailSize(int w, int h) {
    if ((m_nThumbWidth == w) && (m_nThumbHeight == h)) return 0;

    // w == 0 and h == 0 resets thumbnail configuration
    if (((w | h) != 0) && ((w < 16) || (h < 16))) {
        ALOGE("Too small thumbnail image size %dx%d", w, h);
        return -1;
    }

    m_nThumbWidth = w;
    m_nThumbHeight = h;

    if (IsBTBCompressionSupported()) SetState(STATE_THUMBSIZE_CHANGED);

    return 0;
}

int ExynosJpegEncoderForCamera::setThumbnailQuality(int quality) {
    if (m_nThumbQuality == quality) return 0;

    if ((quality > 100) || (quality < 1)) {
        ALOGE("Invalid quality factor %d for thumbnail image", quality);
        return -1;
    }

    m_nThumbQuality = quality;

    return GetCompressor().SetQuality(0, m_nThumbQuality) ? 0 : -1;
}

int ExynosJpegEncoderForCamera::setThumbnailPadding(const unsigned char *padding,
                                                    unsigned int num_planes) {
    return GetCompressor().SetPadding2(padding, num_planes) ? 0 : -1;
}

bool ExynosJpegEncoderForCamera::EnsureFormatIsApplied() {
    if (TestStateEither(STATE_PIXFMT_CHANGED | STATE_SIZE_CHANGED | STATE_THUMBSIZE_CHANGED)) {
        int thumb_width = m_nThumbWidth;
        int thumb_height = m_nThumbHeight;
        int width = 0;
        int height = 0;

        if (IsThumbGenerationNeeded() || !IsBTBCompressionSupported()) {
            thumb_width = 0;
            thumb_height = 0;
        }

        getSize(&width, &height);
        if (!GetCompressor().SetImageFormat(getColorFormat(), width, height, thumb_width,
                                            thumb_height))
            return false;

        ClearState(STATE_PIXFMT_CHANGED | STATE_SIZE_CHANGED | STATE_THUMBSIZE_CHANGED);
    }

    return true;
}

size_t ExynosJpegEncoderForCamera::RemoveTrailingDummies(char *base, size_t len) {
    ALOG_ASSERT(len > 4);
    ALOG_ASSERT((base[0] == 0xFF) && (base[1] == 0xD8)); // SOI marker

    size_t riter = len - 2;

    while (riter > 0) {
        if ((base[riter] == 0xFF) && (base[riter + 1] == 0xD9)) { // EOI marker
            ALOGI_IF(riter < (len - 2), "Found %zu dummies after EOI", len - riter - 2);
            return riter + 2;
        }
        riter--;
    }

    ALOGE("EOI is not found!");
    ALOG_ASSERT(true);

    return 0;
}

void *ExynosJpegEncoderForCamera::tCompressThumbnail(void *p) {
    ExynosJpegEncoderForCamera *encoder = reinterpret_cast<ExynosJpegEncoderForCamera *>(p);

    size_t thumblen = encoder->CompressThumbnail();
    return reinterpret_cast<void *>(thumblen);
}

bool ExynosJpegEncoderForCamera::ProcessExif(char *base, size_t limit, exif_attribute_t *exifInfo,
                                             extra_appinfo_t *extra) {
    // PREREQUISITES: The main and the thumbnail image size should be configured before.

    // Sanity chck
    uint32_t width = 0;
    uint32_t height = 0;

    getSize(reinterpret_cast<int *>(&width), reinterpret_cast<int *>(&height));

    if (exifInfo) {
        if ((exifInfo->width != width) || (exifInfo->height != height)) {
            ALOGE("Inconsistent image dimension: Exif %dx%d, Thumb %dx%d", exifInfo->width,
                  exifInfo->height, width, height);
            return false;
        }

        if (exifInfo->enableThumb) {
            if ((exifInfo->widthThumb != static_cast<uint32_t>(m_nThumbWidth)) ||
                (exifInfo->heightThumb != static_cast<uint32_t>(m_nThumbHeight))) {
                ALOGE("Inconsistent thumbnail information: Exif %dx%d, Thumb %dx%d",
                      exifInfo->widthThumb, exifInfo->heightThumb, m_nThumbWidth, m_nThumbHeight);
                return false;
            }
        }
    }

    // Giving appwriter the address beyond SOS marker
    // because it is handled by this class
    size_t align = 16;
    if (!!(GetDeviceCapabilities() & V4L2_CAP_EXYNOS_JPEG_NO_STREAMBASE_ALIGN)) align = 1;

    m_pAppWriter->PrepareAppWriter(base + JPEG_MARKER_SIZE, exifInfo, extra);

    if (limit <= (m_pAppWriter->CalculateAPPSize(0) + NECESSARY_JPEG_LENGTH)) {
        ALOGE("Too small JPEG stream buffer size, %zu bytes", limit);
        return false;
    }

    bool reserve_thumbspace = true;

    // If the length of the given stream buffer is too small, and thumbnail
    // compression is also required, the compressed stream data of the main
    // image is appeneded after the end of the fields if IFD1. The place is
    // actually reserved for the embedded thumbnail but the main JPEG stream
    // is written in this case because it is unknown how the compressed data
    // of the thumbnail image will be.
    // After the main and the thumbnail image compressions are completed,
    // the compressed data of the main image is shifted by the length of the
    // compressed data of the thumbnail image. Then the compressed data of
    // the thumbnail image is copied to the place for it.
    if (!exifInfo || !exifInfo->enableThumb || (limit < (JPEG_MAX_SEGMENT_SIZE * 10)))
        reserve_thumbspace = false;

    m_pAppWriter->Write(reserve_thumbspace, JPEG_MARKER_SIZE, align, TestState(STATE_HWFC_ENABLED));

    ALOGD("Image compression starts from offset %zu (APPx size %zu, HWFC? %d, NBTB? %d)",
          PTR_DIFF(base, m_pAppWriter->GetMainStreamBase()), m_pAppWriter->CalculateAPPSize(),
          TestState(STATE_HWFC_ENABLED), TestState(STATE_NO_BTBCOMP));

    return true;
}

bool ExynosJpegEncoderForCamera::PrepareCompression(bool thumbnail) {
    if (!thumbnail) return true;

    if (IsThumbGenerationNeeded()) {
        if (pthread_create(&m_threadWorker, NULL, tCompressThumbnail,
                           reinterpret_cast<void *>(this)) != 0) {
            ALOGERR("Failed to create thumbnail generation thread");
            return false;
        }
    } else {
        // allocate temporary thumbnail stream buffer
        // to prevent overflow of the compressed stream
        if (!AllocThumbJpegBuffer()) {
            return false;
        }
    }

    if (!TestState(STATE_NO_BTBCOMP) && IsBTBCompressionSupported()) {
        if (checkOutBufType() == JPEG_BUF_TYPE_USER_PTR) {
            if (!GetCompressor().SetJpegBuffer2(m_pIONThumbJpegBuffer, m_szIONThumbJpegBuffer)) {
                ALOGE("Failed to configure thumbnail buffer @ %p(size %zu)", m_pIONThumbJpegBuffer,
                      m_szIONThumbJpegBuffer);
                return false;
            }
        } else {
            if (!GetCompressor().SetJpegBuffer2(m_fdIONThumbJpegBuffer, m_szIONThumbJpegBuffer)) {
                ALOGE("Failed to configure thumbnail buffer @ %d(size %zu)", m_fdIONThumbJpegBuffer,
                      m_szIONThumbJpegBuffer);
                return false;
            }
        }
    }

    return true;
}

int ExynosJpegEncoderForCamera::encode(int *size, exif_attribute_t *exifInfo, char **pcJpegBuffer,
                                       debug_attribute_t *debugInfo) {
    return encode(size, exifInfo, -1, pcJpegBuffer, debugInfo);
}

int ExynosJpegEncoderForCamera::encode(int *size, exif_attribute_t *exifInfo, int fdJpegBuffer,
                                       char **pcJpegBuffer, debug_attribute_t *debugInfo) {
    if ((!debugInfo) || (debugInfo->num_of_appmarker == 0)) {
        extra_appinfo_t *extra = NULL;
        return encode(size, exifInfo, fdJpegBuffer, pcJpegBuffer, extra);
    }

    m_extraInfo.num_of_appmarker = 0;
    memset(m_appInfo, 0, sizeof(m_appInfo));

    ExtractDebugAttributeInfo(debugInfo, &m_extraInfo);

    return encode(size, exifInfo, fdJpegBuffer, pcJpegBuffer, &m_extraInfo);
}

int ExynosJpegEncoderForCamera::encode(int *size, exif_attribute_t *exifInfo, int fdJpegBuffer,
                                       char **pcJpegBuffer, extra_appinfo_t *appInfo) {
    if (!(*pcJpegBuffer)) {
        ALOGE("Target stream buffer is not specified");
        return -1;
    }

    if (*size <= 0) {
        ALOGE("Too small stram buffer length %d bytes", *size);
        return -1;
    }

    m_pStreamBase = *pcJpegBuffer;
    m_nStreamSize = *size; // contains max buffer length until the compression finishes

    char *jpeg_base = m_pStreamBase;

    ALOGI_IF(!exifInfo, "Exif is not specified. Skipping writing APP1 marker");
    ALOGI_IF(!appInfo, "Debugging information is not specified. Skipping writing APP4 marker");
    ALOGD("Given stream buffer size: %d bytes", *size);

    CStopWatch stopwatch(true);

    if (!ProcessExif(jpeg_base, m_nStreamSize, exifInfo, appInfo)) return -1;

    int offset = PTR_DIFF(m_pStreamBase, m_pAppWriter->GetMainStreamBase());
    int buffsize = static_cast<int>(m_nStreamSize - offset);
    if ((fdJpegBuffer < 0) ||
        !(GetDeviceCapabilities() & V4L2_CAP_EXYNOS_JPEG_DMABUF_OFFSET)) { // JPEG_BUF_TYPE_USER_PTR
        if (setOutBuf(m_pAppWriter->GetMainStreamBase(), buffsize) < 0) {
            ALOGE("Failed to configure stream buffer : fd %d, addr %p, streamSize %d", fdJpegBuffer,
                  m_pAppWriter->GetMainStreamBase(), buffsize);
            return -1;
        }
    } else { // JPEG_BUF_TYPE_DMA_BUF
        if (setOutBuf(fdJpegBuffer, buffsize, offset) < 0) {
            ALOGE("Failed to configure stream buffer : fd %d, addr %p, streamSize %d", fdJpegBuffer,
                  m_pAppWriter->GetMainStreamBase(), buffsize);
            return -1;
        }
    }

    bool block_mode = !TestState(STATE_HWFC_ENABLED);
    bool thumbenc = m_pAppWriter->GetThumbStreamBase() != NULL;
    size_t thumblen = 0;

    //        THUMB REQ? | THUMB IMG GIVEN? | B2B COMP? | HWFC(NONBLOCKING)?
    // CASE1:      O     |        X         |     -     |         X
    // CASE2:      O     |        X         |     -     |         O
    // CASE3:      O     |        O         |     X     |         X
    // CASE4:      O     |        O         |     O     |         X
    // CASE5:      O     |        O         |     O     |         O
    // CASE6:      X     |        -         |     -     |         -
    // CASE7:      O     |        O         |     X     |         O
    //
    // CASE1 = thumbenc && IsThumbGenerationNeeded() && block_mode
    // CASE2 = thumbenc && IsThumbGenerationNeeded() && !block_mode
    // CASE3 = thumbenc && !IsThumbGenerationNeeded() && !IsBTBCompressionSupported() && !block_mode
    // CASE4 = thumbenc && !IsThumbGenerationNeeded() && !STATE_NO_BTBCOMP &&
    // IsBTBCompressionSupported() && !block_mode CASE5 = thumbenc && !IsThumbGenerationNeeded() &&
    // !STATE_NO_BTBCOMP && IsBTBCompressionSupported() && block_mode CASE6 = !thumbenc CASE7 =
    // thumbenc && !IsThumbGenerationNeeded() && STATE_NO_BTBCOMP && block_mode

    if (!thumbenc) {
        // Confirm that no thumbnail information is transferred to HWJPEG
        setThumbnailSize(0, 0);
    } else if (!IsThumbGenerationNeeded() && IsBTBCompressionSupported() &&
               (m_fThumbBufferType != checkInBufType())) {
        ALOGE("Buffer types of thumbnail(%d) and main(%d) images should be the same",
              m_fThumbBufferType, checkInBufType());
        return -1;
    } else if (!IsThumbGenerationNeeded() && (m_fThumbBufferType == 0)) {
        // Thumbnail buffer configuration failed but the client forces to compress with thumbnail
        ThumbGenerationNeeded();
        SetState(STATE_THUMBSIZE_CHANGED);
    }

    if (!EnsureFormatIsApplied()) {
        ALOGE("Failed to confirm format");
        return -1;
    }

    if (!PrepareCompression(thumbenc)) {
        ALOGE("Failed to prepare compression");
        return -1;
    }

    ssize_t mainlen = GetCompressor().Compress(&thumblen, block_mode);
    if (mainlen < 0) {
        ALOGE("Error occured while JPEG compression: %zd", mainlen);
        return -1;
    }

    if (mainlen == 0) { /* non-blocking compression */
        ALOGD("Waiting for MCSC run");
        return 0;
    }

    *size = static_cast<int>(FinishCompression(mainlen, thumblen));
    if (*size < 0) return -1;

    ALOGD("....compression delay(usec.): HW %u, Total %lu)", GetHWDelay(), stopwatch.GetElapsed());

    return 0;
}

ssize_t ExynosJpegEncoderForCamera::FinishCompression(size_t mainlen, size_t thumblen) {
    bool btb = false;
    size_t max_streamsize = m_nStreamSize;
    char *mainbase = m_pAppWriter->GetMainStreamBase();
    char *thumbbase = m_pAppWriter->GetThumbStreamBase();

    m_nStreamSize = 0;

    mainlen = RemoveTrailingDummies(mainbase, mainlen);

    // Clearing SOI of the main image written by H/W
    m_pAppWriter->GetMainStreamBase()[0] = 0;
    m_pAppWriter->GetMainStreamBase()[1] = 0;

    if (thumbbase) {
        if (IsThumbGenerationNeeded()) {
            void *len;
            int ret = pthread_join(m_threadWorker, &len);
            if (ret != 0) {
                ALOGERR("Failed to wait thumbnail thread(%d)", ret);
                return -1;
            }

            if (len == NULL)
                ALOGE("Error occurred during thumbnail creation: no thumbnail is embedded");

            thumblen = reinterpret_cast<size_t>(len);
        } else if (TestState(STATE_NO_BTBCOMP) || !IsBTBCompressionSupported()) {
            thumblen = CompressThumbnailOnly(m_pAppWriter->GetMaxThumbnailSize(), m_nThumbQuality,
                                             getColorFormat(), checkInBufType());
        } else {
            btb = true;
        }

        size_t max_thumb = min(m_pAppWriter->GetMaxThumbnailSize(),
                               max_streamsize - m_pAppWriter->CalculateAPPSize(0) - mainlen);

        if (thumblen > max_thumb) {
            ALOGI("Too large thumbnail (%dx%d) stream size %zu (max: %zu, quality factor %d)",
                  m_nThumbWidth, m_nThumbHeight, thumblen, max_thumb, m_nThumbQuality);
            ALOGI("Retrying thumbnail compression with quality factor 50");
            thumblen = CompressThumbnailOnly(max_thumb, 50, getColorFormat(), checkInBufType());
            if (thumblen == 0) return -1;
        }

        if (!m_pAppWriter->IsThumbSpaceReserved()) {
            if (PTR_TO_ULONG(m_pStreamBase + max_streamsize) <
                PTR_TO_ULONG(mainbase + mainlen + thumblen - JPEG_MARKER_SIZE)) {
                ALOGE("Too small JPEG buffer length %zu (APP %zu, Main %zu, Thumb %zu)",
                      max_streamsize, m_pAppWriter->CalculateAPPSize(thumblen), mainlen, thumblen);
                return -1;
            }

            // the SOI of the stream of the main image is stored after the APP4 or APP11 segment if
            // they exist.
            memmove(m_pAppWriter->GetApp1End() + thumblen, m_pAppWriter->GetApp1End(),
                    mainlen +
                            PTR_DIFF(m_pAppWriter->GetApp1End(),
                                     m_pAppWriter->GetMainStreamBase()));
            m_pAppWriter->UpdateApp1Size(thumblen);

            // m_nAppLength has the value of appwriter.GetExactAPPSize()
            // Therefore m_nStreamSize should be initialized with thumbnail stream length;
        }

        if (thumblen > 0) {
            memcpy(m_pAppWriter->GetThumbStreamBase(), m_pIONThumbJpegBuffer, thumblen);
            m_pAppWriter->Finalize(thumblen);
        }

        if (m_pAppWriter->IsThumbSpaceReserved()) {
            // clear the possible stale data in the dummy area after the thumbnail stream
            memset(m_pAppWriter->GetThumbStreamBase() + thumblen, 0,
                   m_pAppWriter->GetMaxThumbnailSize() - thumblen +
                           m_pAppWriter->GetAPP1ResrevedSize());
        }
    } else {
        thumblen = 0;
    }

    m_nStreamSize += m_pAppWriter->CalculateAPPSize(thumblen) + mainlen;

    /*
     * m_nAppLength: The size of APP1 segment and APP4 segment including markers
     * getJpegSize(): size of the compressed stream of the main image
     * Note that 2 byte(size of SOI marker) is included in APP1 segment size.
     * Thus the size of SOI marker in front of the stream is not added.
     */
    ALOGD("Completed image compression (%zd(thumb %zu) bytes, HWFC? %d, BTB? %d)", mainlen,
          thumblen, TestState(STATE_HWFC_ENABLED), btb);

    m_pStreamBase[0] = 0xFF;
    m_pStreamBase[1] = 0xD8;

    return m_nStreamSize;
}

/* The logic in WaitForHWFC() is the same with encode() */
ssize_t ExynosJpegEncoderForCamera::WaitForCompression() {
    if (!TestState(STATE_HWFC_ENABLED)) return m_nStreamSize;

    size_t thumblen = 0;
    ssize_t streamlen = GetCompressor().WaitForCompression(&thumblen);
    if (streamlen < 0) return streamlen;

    return FinishCompression(streamlen, thumblen);
}

bool ExynosJpegEncoderForCamera::GenerateThumbnailImage() {
    int main_width, main_height;
    if (getSize(&main_width, &main_height) < 0) {
        ALOGE("Failed to get main image size");
        return false;
    }

    int v4l2Format = getColorFormat();

    if (!AllocThumbBuffer(v4l2Format)) return false;

    ALOGD("Generating thumbnail image: %dx%d -> %dx%d", main_width, main_height, m_nThumbWidth,
          m_nThumbHeight);

    if (!mThumbnailScaler) {
        ALOGE("Thumbnail scaler is not prepared");
        return false;
    }

    if (!mThumbnailScaler->SetSrcImage(main_width, main_height, v4l2Format)) {
        ALOGE("Failed to configure the main image to the thumbnail scaler");
        return false;
    }

    if (!mThumbnailScaler->SetDstImage(m_nThumbWidth, m_nThumbHeight,
                                       GetThumbnailFormat(v4l2Format))) {
        ALOGE("Failed to configure the target image to the thumbnail scaler");
        return false;
    }

    bool okay = false;

    if (checkInBufType() == JPEG_BUF_TYPE_USER_PTR) {
        char *bufs[ThumbnailScaler::SCALER_MAX_PLANES];
        int len_srcbufs[ThumbnailScaler::SCALER_MAX_PLANES];

        if (getInBuf(bufs, len_srcbufs, ThumbnailScaler::SCALER_MAX_PLANES) < 0) {
            ALOGE("Failed to retrieve the main image buffers");
            return false;
        }

        okay = mThumbnailScaler->RunStream(bufs, len_srcbufs, m_fdIONThumbImgBuffer,
                                           m_szIONThumbImgBuffer);
    } else { // mainbuftype == JPEG_BUF_TYPE_DMA_BUF
        int bufs[ThumbnailScaler::SCALER_MAX_PLANES];
        int len_srcbufs[ThumbnailScaler::SCALER_MAX_PLANES];

        if (getInBuf(bufs, len_srcbufs, ThumbnailScaler::SCALER_MAX_PLANES) < 0) {
            ALOGE("Failed to retrieve the main image buffers");
            return false;
        }
        okay = mThumbnailScaler->RunStream(bufs, len_srcbufs, m_fdIONThumbImgBuffer,
                                           m_szIONThumbImgBuffer);
    }

    if (!okay) {
        ALOGE("Failed to convert the main image to thumbnail with the thumbnail scaler");
        return false;
    }

    return true;
}

size_t ExynosJpegEncoderForCamera::CompressThumbnail() {
    unsigned int v4l2Format = getColorFormat();
    int buftype = checkInBufType();

    if (IsThumbGenerationNeeded()) {
        if (!GenerateThumbnailImage()) return 0;

        // libcsc output configured by this class is always NV21.
        v4l2Format = GetThumbnailFormat(getColorFormat());

        buftype = JPEG_BUF_TYPE_DMA_BUF;
        // reduced setInBuf2()
        m_fdThumbnailImageBuffer[0] = m_fdIONThumbImgBuffer;
        m_szThumbnailImageLen[0] = m_szIONThumbImgBuffer;
    }

    return CompressThumbnailOnly(m_pAppWriter->GetMaxThumbnailSize(), m_nThumbQuality, v4l2Format,
                                 buftype);
}

bool ExynosJpegEncoderForCamera::AllocThumbBuffer(int v4l2Format) {
    if (m_fdIONClient < 0) {
        ALOGE("ION client is not created");
        return false;
    }

    size_t thumbbufsize = GetImageLength(m_nThumbWidth, m_nThumbHeight, v4l2Format);
    if (thumbbufsize == 0) {
        ALOGE("Unsupported V4L2 format %#X for thumbnail", v4l2Format);
        return false;
    }

    if (m_fdIONThumbImgBuffer >= 0) {
        if (m_szIONThumbImgBuffer >= thumbbufsize) return true;

        if (m_pIONThumbImgBuffer != NULL) munmap(m_pIONThumbImgBuffer, m_szIONThumbImgBuffer);

        close(m_fdIONThumbImgBuffer);

        m_fdIONThumbImgBuffer = -1;
        m_pIONThumbImgBuffer = NULL;
        m_szIONThumbImgBuffer = 0;
    }

    m_fdIONThumbImgBuffer =
            exynos_ion_alloc(m_fdIONClient, thumbbufsize, EXYNOS_ION_HEAP_SYSTEM_MASK, 0);
    if (m_fdIONThumbImgBuffer < 0) {
        ALOGERR("Failed to allocate %zu bytes for NV12 %ux%u", thumbbufsize, m_nThumbHeight,
                m_nThumbWidth);
        m_fdIONThumbImgBuffer = -1;
        return false;
    }

    m_szIONThumbImgBuffer = thumbbufsize;

    return AllocThumbJpegBuffer();
}

bool ExynosJpegEncoderForCamera::AllocThumbJpegBuffer() {
    if (m_fdIONClient < 0) {
        ALOGE("ION client is not created");
        return false;
    }

    size_t thumbbufsize = m_nThumbHeight * m_nThumbWidth * 3;

    if (m_pIONThumbJpegBuffer) {
        if (m_szIONThumbJpegBuffer >= thumbbufsize) return true;

        munmap(m_pIONThumbJpegBuffer, m_szIONThumbJpegBuffer);
        close(m_fdIONThumbJpegBuffer);

        m_szIONThumbJpegBuffer = 0;
        m_pIONThumbJpegBuffer = NULL;
        m_fdIONThumbJpegBuffer = -1;
    }

    m_fdIONThumbJpegBuffer =
            exynos_ion_alloc(m_fdIONClient, thumbbufsize, EXYNOS_ION_HEAP_SYSTEM_MASK,
                             ION_FLAG_CACHED | ION_FLAG_CACHED_NEEDS_SYNC);
    if (m_fdIONThumbJpegBuffer < 0) {
        ALOGERR("Failed to allocate %zu bytes for thumbnail stream buffer of %ux%u", thumbbufsize,
                m_nThumbHeight, m_nThumbWidth);
        return false;
    }

    m_pIONThumbJpegBuffer =
            reinterpret_cast<char *>(mmap(NULL, thumbbufsize, PROT_READ | PROT_WRITE, MAP_SHARED,
                                          m_fdIONThumbJpegBuffer, 0));
    if (m_pIONThumbJpegBuffer == MAP_FAILED) {
        ALOGERR("Failed to map thumbnail stream buffer (%zu bytes)", thumbbufsize);

        m_pIONThumbJpegBuffer = NULL;
    } else {
        m_szIONThumbJpegBuffer = thumbbufsize;
    }

    return m_pIONThumbJpegBuffer != NULL;
}

size_t ExynosJpegEncoderForCamera::CompressThumbnailOnly(size_t limit, int quality,
                                                         unsigned int v4l2Format, int src_buftype) {
    if (!m_phwjpeg4thumb->SetImageFormat(v4l2Format, m_nThumbWidth, m_nThumbHeight)) {
        ALOGE("Failed to configure thumbnail source image format to %#010x, %ux%u", v4l2Format,
              m_nThumbWidth, m_nThumbHeight);
        return 0;
    }

    unsigned int num_buffers = 1;
    switch (v4l2Format) {
        case V4L2_PIX_FMT_YUV420M:
            [[fallthrough]];
        case V4L2_PIX_FMT_YVU420M:
            num_buffers++;
            [[fallthrough]];
        case V4L2_PIX_FMT_NV12M:
            [[fallthrough]];
        case V4L2_PIX_FMT_NV21M:
            num_buffers++;
            break;
    }

    if (src_buftype == JPEG_BUF_TYPE_USER_PTR) {
        if (!m_phwjpeg4thumb->SetImageBuffer(m_pThumbnailImageBuffer, m_szThumbnailImageLen,
                                             num_buffers)) {
            ALOGE("Failed to configure thumbnail buffers(userptr) for thumbnail");
            return 0;
        }
    } else { // JPEG_BUF_TYPE_DMA_BUF
        if (!m_phwjpeg4thumb->SetImageBuffer(m_fdThumbnailImageBuffer, m_szThumbnailImageLen,
                                             num_buffers)) {
            ALOGE("Failed to configure thumbnail buffers(dmabuf) for thumbnail");
            return 0;
        }
    }

    if (!m_phwjpeg4thumb->SetJpegBuffer(m_fdIONThumbJpegBuffer, m_szIONThumbJpegBuffer)) {
        ALOGE("Failed to configure thumbnail stream buffer (fd %d, size %zu)",
              m_fdIONThumbJpegBuffer, m_szIONThumbJpegBuffer);
        return 0;
    }

    // Since the compressed stream of the thumbnail image is to be embedded in
    // APP1 segment, at the end of Exif metadata, the length of the stream should
    // not exceed the maximum length of a segment, 64KB minus the length of Exif
    // metadata. If the stream length is too large, repeat the compression until
    // the length become proper to embed.
    do {
        if (!m_phwjpeg4thumb->SetQuality(quality)) {
            ALOGE("Failed to configure thumbnail quality factor %u", quality);
            return 0;
        }

        ssize_t thumbsize = m_phwjpeg4thumb->Compress();
        if (thumbsize < 0) {
            ALOGE("Failed to compress thumbnail");
            return 0;
        }

        thumbsize = RemoveTrailingDummies(m_pIONThumbJpegBuffer, thumbsize);
        if (static_cast<size_t>(thumbsize) > limit) {
            quality = min(50, quality - 10);
            ALOGI_IF(quality >= 20,
                     "Too large thumbnail stream size %zu. Retrying with quality factor %d...",
                     thumbsize, quality);
        } else {
            return thumbsize;
        }
    } while (quality >= 20);

    ALOG_ASSERT(false, "It should never reach here");
    ALOGE("Thumbnail compression finally failed");

    return 0;
}

int ExynosJpegEncoderForCamera::setInBuf2(int *piBuf, int *iSize) {
    NoThumbGenerationNeeded();

    if (!EnsureFormatIsApplied()) return -1;

    CHWJpegCompressor &hwjpeg = GetCompressor();
    unsigned int num_buffers = 3;
    if (!hwjpeg.GetImageBufferSizes(m_szThumbnailImageLen, &num_buffers)) {
        ALOGE("Failed to get image buffer sizes");
        return -1;
    }

    for (unsigned int i = 0; i < num_buffers; i++) {
        m_szThumbnailImageLen[i] = iSize[i];
        m_fdThumbnailImageBuffer[i] = piBuf[i];
    }

    if (IsBTBCompressionSupported() &&
        !hwjpeg.SetImageBuffer2(m_fdThumbnailImageBuffer, m_szThumbnailImageLen, num_buffers)) {
        ALOGE("Failed to configure thumbnail buffers");
        return -1;
    }

    m_fThumbBufferType = JPEG_BUF_TYPE_DMA_BUF;

    return 0;
}

int ExynosJpegEncoderForCamera::setInBuf2(char **pcBuf, int *iSize) {
    NoThumbGenerationNeeded();

    if (!EnsureFormatIsApplied()) return -1;

    CHWJpegCompressor &hwjpeg = GetCompressor();
    unsigned int num_buffers = 3;
    if (!hwjpeg.GetImageBufferSizes(m_szThumbnailImageLen, &num_buffers)) {
        ALOGE("Failed to get image buffer sizes");
        return -1;
    }

    for (unsigned int i = 0; i < num_buffers; i++) {
        m_szThumbnailImageLen[i] = iSize[i];
        m_pThumbnailImageBuffer[i] = pcBuf[i];
    }

    if (IsBTBCompressionSupported() &&
        !hwjpeg.SetImageBuffer2(m_pThumbnailImageBuffer, m_szThumbnailImageLen, num_buffers)) {
        ALOGE("Failed to configure thumbnail buffers");
        return -1;
    }

    m_fThumbBufferType = JPEG_BUF_TYPE_USER_PTR;

    return 0;
}

size_t ExynosJpegEncoderForCamera::GetThumbnailImage(char *buffer, size_t buflen) {
    if (m_fdIONThumbImgBuffer < 0) {
        ALOGE("No internal thumbnail buffer is allocated");
        return 0;
    }

    size_t thumbbufsize = GetImageLength(m_nThumbWidth, m_nThumbHeight, getColorFormat());
    if (buflen < thumbbufsize) {
        ALOGE("Too small buffer %zu (thumbnail image size %zu)", buflen, thumbbufsize);
        return 0;
    }

    ALOG_ASSERT(m_szIONThumbImgBuffer >= thumbbufsize,
                "m_szIONThumbImgBuffer(%zu) is smaller than the thumbnail (%zu)",
                m_szIONThumbImgBuffer, thumbbufsize);
    if (m_pIONThumbImgBuffer == NULL) {
        m_pIONThumbImgBuffer = reinterpret_cast<char *>(
                mmap(NULL, m_szIONThumbImgBuffer, PROT_READ, MAP_SHARED, m_fdIONThumbImgBuffer, 0));
        if (m_pIONThumbImgBuffer == MAP_FAILED) {
            m_pIONThumbImgBuffer = NULL;
            ALOGERR("Failed to map thumbnail image buffer (%zu bytes)", m_szIONThumbImgBuffer);
            return 0;
        }
    }

    memcpy(buffer, m_pIONThumbImgBuffer, thumbbufsize);

    ALOGD("Copied thumbnail image to %p (%zu bytes)", buffer, thumbbufsize);

    return m_szIONThumbImgBuffer;
}

int ExynosJpegEncoderForCamera::destroy() {
    GetCompressor().Release();
    return 0;
}
