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

#include <exynos-hwjpeg.h>
#include <linux/v4l2-controls.h>
#include <linux/videodev2.h>

#include "hwjpeg-internal.h"
#include "log/log_main.h"

CHWJpegV4L2Compressor::CHWJpegV4L2Compressor()
      : CHWJpegCompressor("/dev/video12"), file_lock_(FileLock(GetDeviceFD())) {
    memset(&m_v4l2Format, 0, sizeof(m_v4l2Format));
    memset(&m_v4l2SrcBuffer, 0, sizeof(m_v4l2SrcBuffer));
    memset(&m_v4l2DstBuffer, 0, sizeof(m_v4l2DstBuffer));
    memset(&m_v4l2SrcPlanes, 0, sizeof(m_v4l2SrcPlanes));
    memset(&m_v4l2DstPlanes, 0, sizeof(m_v4l2DstPlanes));
    memset(&m_v4l2Controls, 0, sizeof(m_v4l2Controls));

    m_v4l2Format.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    // default image format is initialized by 8x8 RGB24 in order for TryFormat()
    // to seccess at anytime.
    // 8x8 : the smallest image size to compress
    // RGB24(or YUV444): The image format with the smallest memory footprint
    //                   without any image size constraints.
    m_v4l2Format.fmt.pix_mp.pixelformat = V4L2_PIX_FMT_RGB24;
    m_v4l2Format.fmt.pix_mp.width = TO_IMAGE_SIZE(16, 0);
    m_v4l2Format.fmt.pix_mp.height = TO_IMAGE_SIZE(16, 0);
    m_v4l2SrcBuffer.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    m_v4l2DstBuffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;

    m_v4l2SrcBuffer.m.planes = m_v4l2SrcPlanes;
    m_v4l2DstBuffer.m.planes = m_v4l2DstPlanes;

    m_uiControlsToSet = 0;

    m_bEnableHWFC = false;

    v4l2_capability cap;
    memset(&cap, 0, sizeof(cap));
    if (ioctl(GetDeviceFD(), VIDIOC_QUERYCAP, &cap) < 0) {
        ALOGERR("Failed to query capability of /dev/video12");
    } else if (!!(cap.capabilities & V4L2_CAP_DEVICE_CAPS)) {
        SetDeviceCapabilities(cap.device_caps);
    }

    // Initialy declare that s_fmt is required.
    SetFlag(HWJPEG_FLAG_PIX_FMT);

    ALOGD("CHWJpegV4L2Compressor Created: %p, FD %d", this, GetDeviceFD());
}

CHWJpegV4L2Compressor::~CHWJpegV4L2Compressor() {
    StopStreaming();

    ALOGD("CHWJpegV4L2Compressor Destroyed: %p, FD %d", this, GetDeviceFD());
}

int CHWJpegV4L2Compressor::lock() {
    mutex_.lock();
    return file_lock_.lock();
}

int CHWJpegV4L2Compressor::unlock() {
    mutex_.unlock();
    return file_lock_.unlock();
}

bool CHWJpegV4L2Compressor::SetChromaSampFactor(unsigned int horizontal, unsigned int vertical) {
    __s32 value;
    switch ((horizontal << 4) | vertical) {
        case 0x00:
            value = V4L2_JPEG_CHROMA_SUBSAMPLING_GRAY;
            break;
        case 0x11:
            value = V4L2_JPEG_CHROMA_SUBSAMPLING_444;
            break;
        case 0x21:
            value = V4L2_JPEG_CHROMA_SUBSAMPLING_422;
            break;
        case 0x22:
            value = V4L2_JPEG_CHROMA_SUBSAMPLING_420;
            break;
        case 0x41:
            value = V4L2_JPEG_CHROMA_SUBSAMPLING_411;
            break;
        case 0x12:
        default:
            ALOGE("Unsupported chroma subsampling %ux%u", horizontal, vertical);
            return false;
    }

    m_v4l2Controls[HWJPEG_CTRL_CHROMFACTOR].id = V4L2_CID_JPEG_CHROMA_SUBSAMPLING;
    m_v4l2Controls[HWJPEG_CTRL_CHROMFACTOR].value = value;
    m_uiControlsToSet |= 1 << HWJPEG_CTRL_CHROMFACTOR;

    return true;
}

bool CHWJpegV4L2Compressor::SetQuality(unsigned int quality_factor, unsigned int quality_factor2) {
    if (quality_factor > 100) {
        ALOGE("Unsupported quality factor %u", quality_factor);
        return false;
    }

    if (quality_factor2 > 100) {
        ALOGE("Unsupported quality factor %u for the secondary image", quality_factor2);
        return false;
    }

    if (quality_factor > 0) {
        m_v4l2Controls[HWJPEG_CTRL_QFACTOR].id = V4L2_CID_JPEG_COMPRESSION_QUALITY;
        m_v4l2Controls[HWJPEG_CTRL_QFACTOR].value = static_cast<__s32>(quality_factor);
        m_uiControlsToSet |= 1 << HWJPEG_CTRL_QFACTOR;
    }

    if (quality_factor2 > 0) {
        m_v4l2Controls[HWJPEG_CTRL_QFACTOR2].id = V4L2_CID_JPEG_SEC_COMP_QUALITY;
        m_v4l2Controls[HWJPEG_CTRL_QFACTOR2].value = static_cast<__s32>(quality_factor2);
        m_uiControlsToSet |= 1 << HWJPEG_CTRL_QFACTOR2;
    }

    return true;
}

bool CHWJpegV4L2Compressor::SetQuality(const unsigned char qtable[]) {
    v4l2_ext_controls ctrls;
    v4l2_ext_control ctrl;

    memset(&ctrls, 0, sizeof(ctrls));
    memset(&ctrl, 0, sizeof(ctrl));

    ctrls.ctrl_class = V4L2_CTRL_CLASS_JPEG;
    ctrls.controls = &ctrl;
    ctrls.count = 1;

    ctrl.id = V4L2_CID_JPEG_QTABLES2;
    ctrl.size = 128; /* two quantization tables */
    ctrl.p_u8 = const_cast<unsigned char *>(qtable);

    if (ioctl(GetDeviceFD(), VIDIOC_S_EXT_CTRLS, &ctrls) < 0) {
        ALOGERR("Failed to configure %u controls", ctrls.count);
        return false;
    }

    return true;
}

bool CHWJpegV4L2Compressor::SetPadding(const unsigned char padding[], unsigned int num_planes) {
    if (num_planes > 3 || num_planes < 1) {
        ALOGE("Attempting to set padding for incorrect number of buffers");
        return false;
    }

    unsigned int padding_value = 0;

    for (int i = num_planes - 1; i >= 0; i--) {
        padding_value <<= 8;
        padding_value |= padding[i];
    }

    m_v4l2Controls[HWJPEG_CTRL_PADDING].id = V4L2_CID_JPEG_PADDING;
    m_v4l2Controls[HWJPEG_CTRL_PADDING].value = static_cast<__s32>(padding_value);
    m_uiControlsToSet |= 1 << HWJPEG_CTRL_PADDING;

    return true;
}

bool CHWJpegV4L2Compressor::SetPadding2(const unsigned char padding[], unsigned int num_planes) {
    if (!IsDeviceCapability(V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION)) {
        ALOGE("Back-to-back compression is not suppored by H/W");
        return false;
    }

    if (num_planes > 3 || num_planes < 1) {
        ALOGE("Attempting to set padding for incorrect number of buffers");
        return false;
    }

    unsigned int padding_value = 0;

    for (int i = num_planes - 1; i >= 0; i--) {
        padding_value <<= 8;
        padding_value |= padding[i];
    }

    m_v4l2Controls[HWJPEG_CTRL_PADDING2].id = V4L2_CID_JPEG_SEC_PADDING;
    m_v4l2Controls[HWJPEG_CTRL_PADDING2].value = static_cast<__s32>(padding_value);
    m_uiControlsToSet |= 1 << HWJPEG_CTRL_PADDING2;

    return true;
}

bool CHWJpegV4L2Compressor::SetImageFormat(unsigned int v4l2_fmt, unsigned int width,
                                           unsigned int height, unsigned int width2,
                                           unsigned int height2) {
    if ((m_v4l2Format.fmt.pix_mp.pixelformat == v4l2_fmt) &&
        (m_v4l2Format.fmt.pix_mp.width == TO_IMAGE_SIZE(width, width2)) &&
        (m_v4l2Format.fmt.pix_mp.height == TO_IMAGE_SIZE(height, height2)))
        return true;

    m_v4l2Format.fmt.pix_mp.pixelformat = v4l2_fmt;
    m_v4l2Format.fmt.pix_mp.width = TO_IMAGE_SIZE(width, width2);
    m_v4l2Format.fmt.pix_mp.height = TO_IMAGE_SIZE(height, height2);

    SetFlag(HWJPEG_FLAG_PIX_FMT);

    return TryFormat();
}

bool CHWJpegV4L2Compressor::GetImageBufferSizes(size_t buf_sizes[], unsigned int *num_buffers) {
    if (buf_sizes) {
        for (unsigned int i = 0; i < m_v4l2Format.fmt.pix_mp.num_planes; i++)
            buf_sizes[i] = m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage;
    }

    if (num_buffers) {
        if (*num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
            ALOGE("The size array length %u is smaller than the number of required buffers %u",
                  *num_buffers, m_v4l2Format.fmt.pix_mp.num_planes);
            return false;
        }

        *num_buffers = m_v4l2Format.fmt.pix_mp.num_planes;
    }

    return true;
}

bool CHWJpegV4L2Compressor::SetImageBuffer(char *buffers[], size_t len_buffers[],
                                           unsigned int num_buffers) {
    if (num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
        ALOGE("The number of buffers %u is smaller than the required %u", num_buffers,
              m_v4l2Format.fmt.pix_mp.num_planes);
        return false;
    }

    for (unsigned int i = 0; i < m_v4l2Format.fmt.pix_mp.num_planes; i++) {
        m_v4l2SrcPlanes[i].m.userptr = reinterpret_cast<unsigned long>(buffers[i]);
        if (len_buffers[i] < m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage) {
            ALOGE("The size of the buffer[%u] %zu is smaller than required %u", i, len_buffers[i],
                  m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage);
            return false;
        }
        m_v4l2SrcPlanes[i].bytesused = m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage;
        m_v4l2SrcPlanes[i].length = len_buffers[i];
    }

    m_v4l2SrcBuffer.memory = V4L2_MEMORY_USERPTR;

    SetFlag(HWJPEG_FLAG_SRC_BUFFER);

    return true;
}

bool CHWJpegV4L2Compressor::SetImageBuffer(int buffers[], size_t len_buffers[],
                                           unsigned int num_buffers) {
    if (num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
        ALOGE("The number of buffers %u is smaller than the required %u", num_buffers,
              m_v4l2Format.fmt.pix_mp.num_planes);
        return false;
    }

    for (unsigned int i = 0; i < m_v4l2Format.fmt.pix_mp.num_planes; i++) {
        m_v4l2SrcPlanes[i].m.fd = buffers[i];
        if (len_buffers[i] < m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage) {
            ALOGE("The size of the buffer[%u] %zu is smaller than required %u", i, len_buffers[i],
                  m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage);
            return false;
        }
        m_v4l2SrcPlanes[i].bytesused = m_v4l2Format.fmt.pix_mp.plane_fmt[i].sizeimage;
        m_v4l2SrcPlanes[i].length = len_buffers[i];
    }

    m_v4l2SrcBuffer.memory = V4L2_MEMORY_DMABUF;

    SetFlag(HWJPEG_FLAG_SRC_BUFFER);

    return true;
}

bool CHWJpegV4L2Compressor::SetImageBuffer2(char *buffers[], size_t len_buffers[],
                                            unsigned int num_buffers) {
    if (!IsDeviceCapability(V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION)) {
        ALOGE("Back-to-back compression is not suppored by H/W");
        return false;
    }

    if (num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
        ALOGE("The number of buffers %u is smaller than the required %u (secondary)", num_buffers,
              m_v4l2Format.fmt.pix_mp.num_planes);
        return false;
    }

    unsigned int ibuf = 0;
    for (unsigned int i = m_v4l2Format.fmt.pix_mp.num_planes;
         i < (m_v4l2Format.fmt.pix_mp.num_planes * 2); i++, ibuf++) {
        m_v4l2SrcPlanes[i].m.userptr = reinterpret_cast<unsigned long>(buffers[ibuf]);
        // size check is ignored for the secondary image buffers
        m_v4l2SrcPlanes[i].bytesused = len_buffers[ibuf];
        m_v4l2SrcPlanes[i].length = len_buffers[ibuf];
    }

    // memory type is only configured by the primary image configuration
    SetFlag(HWJPEG_FLAG_SRC_BUFFER2);

    return true;
}

bool CHWJpegV4L2Compressor::SetImageBuffer2(int buffers[], size_t len_buffers[],
                                            unsigned int num_buffers) {
    if (!IsDeviceCapability(V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION)) {
        ALOGE("Back-to-back compression is not suppored by H/W");
        return false;
    }

    if (num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
        ALOGE("The number of buffers %u is smaller than the required %u (secondary)", num_buffers,
              m_v4l2Format.fmt.pix_mp.num_planes);
        return false;
    }

    unsigned int ibuf = 0;
    for (unsigned int i = m_v4l2Format.fmt.pix_mp.num_planes;
         i < (m_v4l2Format.fmt.pix_mp.num_planes * 2); i++, ibuf++) {
        m_v4l2SrcPlanes[i].m.fd = buffers[ibuf];
        // size check is ignored for the secondary image buffers
        m_v4l2SrcPlanes[i].bytesused = len_buffers[ibuf];
        m_v4l2SrcPlanes[i].length = len_buffers[ibuf];
    }

    // memory type is only configured by the primary image configuration

    SetFlag(HWJPEG_FLAG_SRC_BUFFER2);

    return true;
}

bool CHWJpegV4L2Compressor::SetJpegBuffer(char *buffer, size_t len_buffer) {
    m_v4l2DstPlanes[0].m.userptr = reinterpret_cast<unsigned long>(buffer);
    m_v4l2DstPlanes[0].length = len_buffer;
    m_v4l2DstBuffer.memory = V4L2_MEMORY_USERPTR;
    SetFlag(HWJPEG_FLAG_DST_BUFFER);
    return true;
}

bool CHWJpegV4L2Compressor::SetJpegBuffer(int buffer, size_t len_buffer, int offset) {
    m_v4l2DstPlanes[0].m.fd = buffer;
    m_v4l2DstPlanes[0].length = len_buffer;
    m_v4l2DstPlanes[0].data_offset = offset;
    m_v4l2DstBuffer.memory = V4L2_MEMORY_DMABUF;
    SetFlag(HWJPEG_FLAG_DST_BUFFER);
    return true;
}

bool CHWJpegV4L2Compressor::SetJpegBuffer2(char *buffer, size_t len_buffer) {
    if (!IsDeviceCapability(V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION)) {
        ALOGE("Back-to-back compression is not suppored by H/W");
        return false;
    }

    m_v4l2DstPlanes[1].m.userptr = reinterpret_cast<unsigned long>(buffer);
    m_v4l2DstPlanes[1].length = len_buffer;
    SetFlag(HWJPEG_FLAG_DST_BUFFER2);
    return true;
}

bool CHWJpegV4L2Compressor::SetJpegBuffer2(int buffer, size_t len_buffer) {
    if (!IsDeviceCapability(V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION)) {
        ALOGE("Back-to-back compression is not suppored by H/W");
        return false;
    }

    m_v4l2DstPlanes[1].m.fd = buffer;
    m_v4l2DstPlanes[1].length = len_buffer;
    SetFlag(HWJPEG_FLAG_DST_BUFFER2);
    return true;
}

bool CHWJpegV4L2Compressor::StopStreaming() {
    if (TestFlag(HWJPEG_FLAG_STREAMING)) {
        if (!StreamOff()) return false;
        ClearFlag(HWJPEG_FLAG_STREAMING);
    }

    // Stream off dequeues all queued buffers
    ClearFlag(HWJPEG_FLAG_QBUF_OUT | HWJPEG_FLAG_QBUF_CAP);

    // It is OK to skip DQBUF because STREAMOFF dequeues all queued buffers
    if (TestFlag(HWJPEG_FLAG_REQBUFS)) {
        if (!ReqBufs(0)) return false;
        ClearFlag(HWJPEG_FLAG_REQBUFS);
    }

    return true;
}

ssize_t CHWJpegV4L2Compressor::Compress(size_t *secondary_stream_size, bool block_mode) {
    if (TestFlag(HWJPEG_FLAG_PIX_FMT)) {
        if (!StopStreaming() || !SetFormat()) return -1;
    }

    if (!TestFlag(HWJPEG_FLAG_SRC_BUFFER)) {
        ALOGE("Source image buffer is not specified");
        return -1;
    }

    if (!TestFlag(HWJPEG_FLAG_DST_BUFFER)) {
        ALOGE("Output JPEG stream buffer is not specified");
        return -1;
    }

    m_v4l2SrcBuffer.length = m_v4l2Format.fmt.pix_mp.num_planes;
    m_v4l2DstBuffer.length = 1;
    if (IsB2BCompression()) {
        if (!TestFlag(HWJPEG_FLAG_SRC_BUFFER2 | HWJPEG_FLAG_DST_BUFFER2)) {
            ALOGE("Either of source or destination buffer of secondary image is not specified "
                  "(%#x)",
                  GetFlags());
            return -1;
        }
        // The SMFC Driver expects the number of buffers to be doubled
        // if back-to-back compression is enabled
        m_v4l2SrcBuffer.length *= 2;
        m_v4l2DstBuffer.length = 2;
    }

    if (!!(GetAuxFlags() & EXYNOS_HWJPEG_AUXOPT_SRC_NOCACHECLEAN))
        m_v4l2SrcBuffer.flags |= V4L2_BUF_FLAG_NO_CACHE_CLEAN;
    if (!!(GetAuxFlags() & EXYNOS_HWJPEG_AUXOPT_DST_NOCACHECLEAN))
        m_v4l2DstBuffer.flags |= V4L2_BUF_FLAG_NO_CACHE_CLEAN;

    if (!ReqBufs() || !StreamOn() || !UpdateControls() || !QBuf()) return -1;

    return block_mode ? DQBuf(secondary_stream_size) : 0;
}

bool CHWJpegV4L2Compressor::TryFormat() {
    if (ioctl(GetDeviceFD(), VIDIOC_TRY_FMT, &m_v4l2Format) < 0) {
        ALOGERR("Failed to TRY_FMT for compression");
        return false;
    }

    return true;
}

bool CHWJpegV4L2Compressor::SetFormat() {
    if (ioctl(GetDeviceFD(), VIDIOC_S_FMT, &m_v4l2Format) < 0) {
        ALOGERR("Failed to S_FMT for image to compress");
        return false;
    }

    v4l2_format v4l2JpegFormat;
    memset(&v4l2JpegFormat, 0, sizeof(v4l2JpegFormat));

    v4l2JpegFormat.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    v4l2JpegFormat.fmt.pix_mp.pixelformat = V4L2_PIX_FMT_JPEG;
    v4l2JpegFormat.fmt.pix_mp.width = m_v4l2Format.fmt.pix_mp.width;
    v4l2JpegFormat.fmt.pix_mp.height = m_v4l2Format.fmt.pix_mp.height;

    if (ioctl(GetDeviceFD(), VIDIOC_S_FMT, &v4l2JpegFormat) < 0) {
        ALOGERR("Failed to S_FMT for JPEG stream to capture");
        return false;
    }

    ClearFlag(HWJPEG_FLAG_PIX_FMT);

    return true;
}

bool CHWJpegV4L2Compressor::UpdateControls() {
    bool enable_hwfc = !!(GetAuxFlags() & EXYNOS_HWJPEG_AUXOPT_ENABLE_HWFC);

    if ((m_uiControlsToSet == 0) && (enable_hwfc == m_bEnableHWFC)) return true;

    v4l2_ext_controls ctrls;
    v4l2_ext_control ctrl[HWJPEG_CTRL_NUM];

    memset(&ctrls, 0, sizeof(ctrls));
    memset(&ctrl, 0, sizeof(ctrl));

    ctrls.ctrl_class = V4L2_CTRL_CLASS_JPEG;
    ctrls.controls = ctrl;
    unsigned int idx_ctrl = 0;
    while (m_uiControlsToSet != 0) {
        if (m_uiControlsToSet & (1 << idx_ctrl)) {
            ctrl[ctrls.count].id = m_v4l2Controls[idx_ctrl].id;
            ctrl[ctrls.count].value = m_v4l2Controls[idx_ctrl].value;
            m_uiControlsToSet &= ~(1 << idx_ctrl);
            ctrls.count++;
        }
        idx_ctrl++;
    }

    if (m_bEnableHWFC != enable_hwfc) {
        m_bEnableHWFC = enable_hwfc;
        ctrl[ctrls.count].id = V4L2_CID_JPEG_HWFC_ENABLE;
        ctrl[ctrls.count].value = m_bEnableHWFC ? 1 : 0;
        ctrls.count++;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_S_EXT_CTRLS, &ctrls) < 0) {
        ALOGERR("Failed to configure %u controls", ctrls.count);
        return false;
    }

    return true;
}

bool CHWJpegV4L2Compressor::ReqBufs(unsigned int count) {
    // - count > 0 && REQBUFS is set: Just return true
    // - count > 0 && REQBUFS is unset: REQBUFS(count) is required
    // - count == 0 && REQBUFS is set: REQBUFS(0) is required
    // - count == 0 && REQBUFS is unset: Just return true;
    if ((count > 0) == TestFlag(HWJPEG_FLAG_REQBUFS)) return true;

    v4l2_requestbuffers reqbufs;

    memset(&reqbufs, 0, sizeof(reqbufs));
    reqbufs.count = count;
    reqbufs.memory = m_v4l2SrcBuffer.memory;
    reqbufs.type = m_v4l2SrcBuffer.type;
    if (ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &reqbufs) < 0) {
        ALOGERR("Failed to REQBUFS(%u) of the source image", count);
        return false;
    }

    memset(&reqbufs, 0, sizeof(reqbufs));
    reqbufs.count = count;
    reqbufs.memory = m_v4l2DstBuffer.memory;
    reqbufs.type = m_v4l2DstBuffer.type;
    if (ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &reqbufs) < 0) {
        ALOGERR("Failed to REQBUFS(%u) of the JPEG stream", count);
        // rolling back the reqbufs for the source image
        reqbufs.memory = m_v4l2SrcBuffer.memory;
        reqbufs.type = m_v4l2SrcBuffer.type;
        reqbufs.count = 0;
        ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &reqbufs); // don't care if it fails
        return false;
    }

    if (count > 0)
        SetFlag(HWJPEG_FLAG_REQBUFS);
    else
        ClearFlag(HWJPEG_FLAG_REQBUFS);

    return true;
}

bool CHWJpegV4L2Compressor::StreamOn() {
    if (TestFlag(HWJPEG_FLAG_STREAMING)) return true;

    if (!TestFlag(HWJPEG_FLAG_REQBUFS)) {
        ALOGE("Trying to STREAMON before REQBUFS");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_STREAMON, &m_v4l2SrcBuffer.type) < 0) {
        ALOGERR("Failed to STREAMON for the source image");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_STREAMON, &m_v4l2DstBuffer.type) < 0) {
        ALOGERR("Failed to STREAMON for the JPEG stream");
        ioctl(GetDeviceFD(), VIDIOC_STREAMOFF, &m_v4l2SrcBuffer.type);
        return false;
    }

    SetFlag(HWJPEG_FLAG_STREAMING);

    return true;
}

bool CHWJpegV4L2Compressor::StreamOff() {
    if (!TestFlag(HWJPEG_FLAG_STREAMING)) return true;

    // error during stream off do not need further handling because of nothing to do
    if (ioctl(GetDeviceFD(), VIDIOC_STREAMOFF, &m_v4l2SrcBuffer.type) < 0)
        ALOGERR("Failed to STREAMOFF for the source image");

    if (ioctl(GetDeviceFD(), VIDIOC_STREAMOFF, &m_v4l2DstBuffer.type) < 0)
        ALOGERR("Failed to STREAMOFF for the JPEG stream");

    ClearFlag(HWJPEG_FLAG_STREAMING);

    return true;
}

bool CHWJpegV4L2Compressor::QBuf() {
    if (!TestFlag(HWJPEG_FLAG_REQBUFS)) {
        ALOGE("QBuf is not permitted until REQBUFS is performed");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_QBUF, &m_v4l2SrcBuffer) < 0) {
        ALOGERR("QBuf of the source buffers is failed (B2B %s)",
                IsB2BCompression() ? "enabled" : "disabled");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_QBUF, &m_v4l2DstBuffer) < 0) {
        ALOGERR("QBuf of the JPEG buffers is failed (B2B %s)",
                IsB2BCompression() ? "enabled" : "disabled");
        // Reqbufs(0) is the only way to cancel the previous queued buffer
        StopStreaming();
        return false;
    }

    SetFlag(HWJPEG_FLAG_QBUF_OUT | HWJPEG_FLAG_QBUF_CAP);

    return true;
}

ssize_t CHWJpegV4L2Compressor::DQBuf(size_t *secondary_stream_size) {
    bool failed = false;
    v4l2_buffer buffer_src, buffer_dst;
    v4l2_plane planes_src[6], planes_dst[2];

    ALOG_ASSERT(TestFlag(HWJPEG_FLAG_QBUF_OUT) == TestFlag(HWJPEG_FLAG_QBUF_CAP));

    memset(&buffer_src, 0, sizeof(buffer_src));
    memset(&buffer_dst, 0, sizeof(buffer_dst));
    memset(&planes_src, 0, sizeof(planes_src));
    memset(&planes_dst, 0, sizeof(planes_dst));

    buffer_src.type = m_v4l2SrcBuffer.type;
    buffer_src.memory = m_v4l2SrcBuffer.memory;
    buffer_src.length = m_v4l2SrcBuffer.length;
    buffer_src.m.planes = planes_src;

    buffer_dst.type = m_v4l2DstBuffer.type;
    buffer_dst.memory = m_v4l2DstBuffer.memory;
    buffer_dst.length = m_v4l2DstBuffer.length;
    buffer_dst.m.planes = planes_dst;

    if (TestFlag(HWJPEG_FLAG_QBUF_OUT) && (ioctl(GetDeviceFD(), VIDIOC_DQBUF, &buffer_src) < 0)) {
        ALOGERR("Failed to DQBUF of the image buffer");
        failed = true;
    }

    if (TestFlag(HWJPEG_FLAG_QBUF_CAP) && (ioctl(GetDeviceFD(), VIDIOC_DQBUF, &buffer_dst) < 0)) {
        ALOGERR("Failed to DQBUF of the JPEG stream buffer");
        failed = true;
    }

    ClearFlag(HWJPEG_FLAG_QBUF_OUT | HWJPEG_FLAG_QBUF_CAP);

    if (failed) return -1;

    if (!!((buffer_src.flags | buffer_dst.flags) & V4L2_BUF_FLAG_ERROR)) {
        ALOGE("Error occurred during compression");
        return -1;
    }

    // We don't need to check the length of secondary stream
    // because it will be zero if the secondary image is not processed.
    SetStreamSize(buffer_dst.m.planes[0].bytesused, buffer_dst.m.planes[1].bytesused);

    // The driver stores the delay in usec. of JPEG compression by H/W
    // to v4l2_buffer.reserved2.
    m_uiHWDelay = buffer_dst.reserved2;

    return GetStreamSize(secondary_stream_size);
}

ssize_t CHWJpegV4L2Compressor::WaitForCompression(size_t *secondary_stream_size) {
    return DQBuf(secondary_stream_size);
}

bool CHWJpegV4L2Compressor::GetImageBuffers(int buffers[], size_t len_buffers[],
                                            unsigned int num_buffers) {
    if (m_v4l2SrcBuffer.memory != V4L2_MEMORY_DMABUF) {
        ALOGE("Current image buffer type is not dma-buf but attempted to retrieve dma-buf buffers");
        return false;
    }

    if (num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
        ALOGE("Number of planes are %u but attemts to retrieve %u buffers",
              m_v4l2Format.fmt.pix_mp.num_planes, num_buffers);
        return false;
    }

    for (unsigned int i = 0; i < m_v4l2Format.fmt.pix_mp.num_planes; i++) {
        buffers[i] = m_v4l2SrcBuffer.m.planes[i].m.fd;
        len_buffers[i] = m_v4l2SrcBuffer.m.planes[i].length;
    }

    return true;
}

bool CHWJpegV4L2Compressor::GetImageBuffers(char *buffers[], size_t len_buffers[],
                                            unsigned int num_buffers) {
    if (m_v4l2SrcBuffer.memory != V4L2_MEMORY_USERPTR) {
        ALOGE("Current image buffer type is not userptr but attempted to retrieve userptr buffers");
        return false;
    }

    if (num_buffers < m_v4l2Format.fmt.pix_mp.num_planes) {
        ALOGE("Number of planes are %u but attemts to retrieve %u buffers",
              m_v4l2Format.fmt.pix_mp.num_planes, num_buffers);
        return false;
    }

    for (unsigned int i = 0; i < m_v4l2Format.fmt.pix_mp.num_planes; i++) {
        buffers[i] = reinterpret_cast<char *>(m_v4l2SrcBuffer.m.planes[i].m.userptr);
        len_buffers[i] = m_v4l2SrcBuffer.m.planes[i].length;
    }

    return true;
}

bool CHWJpegV4L2Compressor::GetJpegBuffer(int *buffer, size_t *len_buffer) {
    if (m_v4l2DstBuffer.memory != V4L2_MEMORY_DMABUF) {
        ALOGE("Current jpeg buffer type is not dma-buf but attempted to retrieve dma-buf buffer");
        return false;
    }

    *buffer = m_v4l2DstBuffer.m.planes[0].m.fd;
    *len_buffer = m_v4l2DstBuffer.m.planes[0].length;

    return true;
}

bool CHWJpegV4L2Compressor::GetJpegBuffer(char **buffer, size_t *len_buffer) {
    if (m_v4l2DstBuffer.memory != V4L2_MEMORY_USERPTR) {
        ALOGE("Current jpeg buffer type is not userptr but attempted to retrieve userptr buffer");
        return false;
    }

    *buffer = reinterpret_cast<char *>(m_v4l2DstBuffer.m.planes[0].m.userptr);
    *len_buffer = m_v4l2DstBuffer.m.planes[0].length;

    return true;
}

void CHWJpegV4L2Compressor::Release() {
    StopStreaming();
}

/******************************************************************************/
/********* D E C O M P R E S S I O N   S U P P O R T **************************/
/******************************************************************************/

CHWJpegV4L2Decompressor::CHWJpegV4L2Decompressor() : CHWJpegDecompressor("/dev/video12") {
    m_v4l2Format.type = 0; // inidication of uninitialized state

    memset(&m_v4l2DstBuffer, 0, sizeof(m_v4l2DstBuffer));
    m_v4l2DstBuffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

    if (Okay()) {
        v4l2_capability cap;
        memset(&cap, 0, sizeof(cap));
        if (ioctl(GetDeviceFD(), VIDIOC_QUERYCAP, &cap) < 0) {
            ALOGERR("Failed to query capability of /dev/video12");
        } else if (!!(cap.capabilities & V4L2_CAP_DEVICE_CAPS)) {
            SetDeviceCapabilities(cap.device_caps);
        }
    }
}

CHWJpegV4L2Decompressor::~CHWJpegV4L2Decompressor() {
    CancelCapture();
}

bool CHWJpegV4L2Decompressor::PrepareCapture() {
    if (m_v4l2DstBuffer.length < m_v4l2Format.fmt.pix.sizeimage) {
        ALOGE("The size of the buffer %u is smaller than required %u", m_v4l2DstBuffer.length,
              m_v4l2Format.fmt.pix.sizeimage);
        return false;
    }

    if (TestFlag(HWJPEG_FLAG_CAPTURE_READY)) return true;

    v4l2_requestbuffers reqbufs;

    memset(&reqbufs, 0, sizeof(reqbufs));
    reqbufs.count = 1;
    reqbufs.memory = m_v4l2DstBuffer.memory;
    reqbufs.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

    if (ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &reqbufs) < 0) {
        ALOGERR("Failed to REQBUFS for the decompressed image");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_STREAMON, &reqbufs.type) < 0) {
        ALOGERR("Failed to STREAMON for the decompressed image");
        reqbufs.count = 0;
        ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &reqbufs);
        return false;
    }

    SetFlag(HWJPEG_FLAG_CAPTURE_READY);

    return true;
}

void CHWJpegV4L2Decompressor::CancelCapture() {
    if (!TestFlag(HWJPEG_FLAG_CAPTURE_READY)) return;

    v4l2_requestbuffers reqbufs;

    memset(&reqbufs, 0, sizeof(reqbufs));
    reqbufs.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    reqbufs.memory = m_v4l2DstBuffer.memory;

    ioctl(GetDeviceFD(), VIDIOC_STREAMOFF, &reqbufs.type);
    ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &reqbufs);

    ClearFlag(HWJPEG_FLAG_CAPTURE_READY);
}

bool CHWJpegV4L2Decompressor::SetImageFormat(unsigned int v4l2_fmt, unsigned int width,
                                             unsigned int height) {
    // Test if new format is the same as the current configured format
    if (m_v4l2Format.type != 0) {
        v4l2_pix_format *p = &m_v4l2Format.fmt.pix;
        if ((p->pixelformat == v4l2_fmt) && (p->width == width) && (p->height == height))
            return true;
    }

    CancelCapture();

    memset(&m_v4l2Format, 0, sizeof(m_v4l2Format));

    m_v4l2Format.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    m_v4l2Format.fmt.pix.pixelformat = v4l2_fmt;
    m_v4l2Format.fmt.pix.width = width;
    m_v4l2Format.fmt.pix.height = height;

    if (ioctl(GetDeviceFD(), VIDIOC_S_FMT, &m_v4l2Format) < 0) {
        ALOGERR("Failed to S_FMT for decompressed image (%08X,%ux%u)", v4l2_fmt, width, height);
        return false;
    }

    return true;
}

bool CHWJpegV4L2Decompressor::SetImageBuffer(char *buffer, size_t len_buffer) {
    m_v4l2DstBuffer.m.userptr = reinterpret_cast<unsigned long>(buffer);
    m_v4l2DstBuffer.bytesused = m_v4l2Format.fmt.pix.sizeimage;
    m_v4l2DstBuffer.length = len_buffer;
    m_v4l2DstBuffer.memory = V4L2_MEMORY_USERPTR;

    return true;
}

bool CHWJpegV4L2Decompressor::SetImageBuffer(int buffer, size_t len_buffer) {
    m_v4l2DstBuffer.m.fd = buffer;
    m_v4l2DstBuffer.bytesused = m_v4l2Format.fmt.pix.sizeimage;
    m_v4l2DstBuffer.length = len_buffer;
    m_v4l2DstBuffer.memory = V4L2_MEMORY_DMABUF;

    return true;
}

bool CHWJpegV4L2Decompressor::PrepareStream() {
    if (TestFlag(HWJPEG_FLAG_OUTPUT_READY)) return true;

    /*
     * S_FMT for output stream is unneccessary because the driver assumes that
     * the current mode is decompression if the capture stream is uncompressed
     * format
     */

    v4l2_requestbuffers rb;
    memset(&rb, 0, sizeof(rb));

    rb.count = 1;
    rb.memory = V4L2_MEMORY_USERPTR;
    rb.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;

    // REQBUFS fails if no S_FMT is not performed
    if (ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &rb) < 0) {
        ALOGERR("Failed to REQBUFS for the JPEG stream.");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_STREAMON, &rb.type) < 0) {
        ALOGERR("Failed to STREAMON for the JPEG stream.");

        rb.count = 0;
        // don't care if reqbufs(0) fails.
        ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &rb);

        return false;
    }

    SetFlag(HWJPEG_FLAG_OUTPUT_READY);

    return true;
}

void CHWJpegV4L2Decompressor::CancelStream() {
    if (!TestFlag(HWJPEG_FLAG_OUTPUT_READY)) return;

    v4l2_requestbuffers rb;
    memset(&rb, 0, sizeof(rb));
    rb.count = 0;
    rb.memory = V4L2_MEMORY_USERPTR;
    rb.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;

    // ignore error during canceling
    ioctl(GetDeviceFD(), VIDIOC_STREAMOFF, &rb.type);
    ioctl(GetDeviceFD(), VIDIOC_REQBUFS, &rb);

    ClearFlag(HWJPEG_FLAG_OUTPUT_READY);
}

bool CHWJpegV4L2Decompressor::QBufAndWait(const char *buffer, size_t len) {
    v4l2_buffer buf;
    memset(&buf, 0, sizeof(buf));
    buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    buf.memory = V4L2_MEMORY_USERPTR;
    buf.bytesused = len;
    buf.m.userptr = reinterpret_cast<unsigned long>(buffer);
    buf.length = len;

    if (ioctl(GetDeviceFD(), VIDIOC_QBUF, &buf) < 0) {
        ALOGERR("Failed to QBUF for the JPEG stream");
        return false;
    }

    if (ioctl(GetDeviceFD(), VIDIOC_QBUF, &m_v4l2DstBuffer) < 0) {
        CancelStream();
        ALOGERR("Failed to QBUF for the decompressed image");
        return false;
    }

    bool ret = true;

    if (ioctl(GetDeviceFD(), VIDIOC_DQBUF, &buf) < 0) {
        ALOGERR("Failed to DQBUF of the stream buffer");
        ret = false;
    }

    buf.type = m_v4l2DstBuffer.type;
    buf.memory = m_v4l2DstBuffer.memory;

    if (ioctl(GetDeviceFD(), VIDIOC_DQBUF, &buf) < 0) {
        ALOGERR("Failed to DQBUF of the image buffer");
        ret = false;
    }

    m_uiHWDelay = buf.reserved2;

    return ret;
}

bool CHWJpegV4L2Decompressor::Decompress(const char *buffer, size_t len) {
    if (m_v4l2Format.type == 0) {
        ALOGE("Decompressed image format is not specified");
        return false;
    }

    if (m_v4l2DstBuffer.length == 0) {
        ALOGE("Decompressed image buffer is not specified");
        return false;
    }

    // Do not change the order of PrepareCapture() and PrepareStream().
    // Otherwise, decompression will fail.
    if (!PrepareCapture() || !PrepareStream()) return false;

    if (!QBufAndWait(buffer, len)) return false;

    return true;
}
