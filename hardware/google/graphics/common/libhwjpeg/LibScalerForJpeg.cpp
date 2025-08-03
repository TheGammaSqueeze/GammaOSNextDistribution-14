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

#include "LibScalerForJpeg.h"

#include "hwjpeg-internal.h"

#define SCALER_DEV_NODE "/dev/video50"

static const char *getBufTypeString(unsigned int buftype) {
    if (buftype == V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE) return "destination";
    if (buftype == V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE) return "source";
    return "unknown";
}

bool LibScalerForJpeg::RunStream(int srcBuf[SCALER_MAX_PLANES],
                                 int __unused srcLen[SCALER_MAX_PLANES], int dstBuf,
                                 size_t __unused dstLen) {
    if (!mSrcImage.begin(V4L2_MEMORY_DMABUF) || !mDstImage.begin(V4L2_MEMORY_DMABUF)) return false;

    return queue(srcBuf, dstBuf);
}

bool LibScalerForJpeg::RunStream(char *srcBuf[SCALER_MAX_PLANES],
                                 int __unused srcLen[SCALER_MAX_PLANES], int dstBuf,
                                 size_t __unused dstLen) {
    if (!mSrcImage.begin(V4L2_MEMORY_USERPTR) || !mDstImage.begin(V4L2_MEMORY_DMABUF)) return false;

    return queue(srcBuf, dstBuf);
}

bool LibScalerForJpeg::Image::set(unsigned int width, unsigned int height, unsigned int format) {
    if (same(width, height, format)) return true;

    if (memoryType != 0) {
        if (!mDevice.requestBuffers(bufferType, memoryType, 0)) return false;
    }

    if (!mDevice.setFormat(bufferType, format, width, height, planeLen)) return false;

    memoryType = 0; // new reqbufs is required.

    return true;
}

bool LibScalerForJpeg::Image::begin(unsigned int memtype) {
    if (memoryType != memtype) {
        if (memoryType != 0) {
            if (!mDevice.requestBuffers(bufferType, memoryType, 0)) return false;
        }

        if (!mDevice.requestBuffers(bufferType, memtype, 1)) return false;

        if (!mDevice.streamOn(bufferType)) return false;

        memoryType = memtype;
    }

    return true;
}

bool LibScalerForJpeg::Image::cancelBuffer() {
    if (!mDevice.streamOff(bufferType)) return false;

    if (!mDevice.streamOn(bufferType)) return false;

    return true;
}

LibScalerForJpeg::Device::Device() {
    mFd = ::open(SCALER_DEV_NODE, O_RDWR);
    if (mFd < 0) ALOGERR("failed to open %s", SCALER_DEV_NODE);
}

LibScalerForJpeg::Device::~Device() {
    if (mFd >= 0) ::close(mFd);
}

bool LibScalerForJpeg::Device::requestBuffers(unsigned int buftype, unsigned int memtype,
                                              unsigned int count) {
    // count==0 means this port should be reconfigured and it is successful under streaming is
    // finished.
    if (!count) streamOff(buftype);

    v4l2_requestbuffers reqbufs{};

    reqbufs.type = buftype;
    reqbufs.memory = memtype;
    reqbufs.count = count;

    if (ioctl(mFd, VIDIOC_REQBUFS, &reqbufs) < 0) {
        ALOGERR("failed REQBUFS(%s, mem=%d, count=%d)", getBufTypeString(buftype), memtype, count);
        return false;
    }

    return true;
}

bool LibScalerForJpeg::Device::setFormat(unsigned int buftype, unsigned int format,
                                         unsigned int width, unsigned int height,
                                         unsigned int planelen[SCALER_MAX_PLANES]) {
    v4l2_format fmt{};

    fmt.type = buftype;
    fmt.fmt.pix_mp.pixelformat = format;
    fmt.fmt.pix_mp.width = width;
    fmt.fmt.pix_mp.height = height;

    if (ioctl(mFd, VIDIOC_S_FMT, &fmt) < 0) {
        ALOGERR("failed S_FMT(%s, fmt=h'%x, %ux%u)", getBufTypeString(buftype), format, width,
                height);
        return false;
    }

    for (uint32_t i = 0; i < fmt.fmt.pix_mp.num_planes; i++) {
        planelen[i] = fmt.fmt.pix_mp.plane_fmt[i].sizeimage;
    }

    return true;
}

bool LibScalerForJpeg::Device::streamOn(unsigned int buftype) {
    if (ioctl(mFd, VIDIOC_STREAMON, &buftype) < 0) {
        ALOGERR("failed STREAMON for %s", getBufTypeString(buftype));
        return false;
    }

    return true;
}

bool LibScalerForJpeg::Device::streamOff(unsigned int buftype) {
    if (ioctl(mFd, VIDIOC_STREAMOFF, &buftype) < 0) {
        ALOGERR("failed STREAMOFF for %s", getBufTypeString(buftype));
        return false;
    }

    return true;
}

bool LibScalerForJpeg::Device::queueBuffer(unsigned int buftype,
                                           std::function<void(v4l2_buffer &)> bufferFiller) {
    v4l2_buffer buffer{};
    v4l2_plane plane[SCALER_MAX_PLANES];

    memset(&plane, 0, sizeof(plane));

    buffer.type = buftype;
    buffer.m.planes = plane;

    bufferFiller(buffer);

    return ioctl(mFd, VIDIOC_QBUF, &buffer) >= 0;
}

bool LibScalerForJpeg::Device::queueBuffer(unsigned int buftype, int buf[SCALER_MAX_PLANES],
                                           unsigned int len[SCALER_MAX_PLANES]) {
    if (!queueBuffer(buftype, [buf, len](v4l2_buffer &buffer) {
            buffer.memory = V4L2_MEMORY_DMABUF;
            buffer.length = SCALER_MAX_PLANES;
            for (unsigned int i = 0; i < SCALER_MAX_PLANES; i++) {
                buffer.m.planes[i].m.fd = buf[i];
                buffer.m.planes[i].length = len[i];
            }
        })) {
        ALOGERR("failed QBUF(%s, fd[]=%d %d, len[0]=%d %d)", getBufTypeString(buftype), buf[0],
                buf[1], len[0], len[1]);
        return false;
    }

    return true;
}

bool LibScalerForJpeg::Device::queueBuffer(unsigned int buftype, char *buf[SCALER_MAX_PLANES],
                                           unsigned int len[SCALER_MAX_PLANES]) {
    if (!queueBuffer(buftype, [buf, len](v4l2_buffer &buffer) {
            buffer.memory = V4L2_MEMORY_USERPTR;
            buffer.length = SCALER_MAX_PLANES;
            for (unsigned int i = 0; i < SCALER_MAX_PLANES; i++) {
                buffer.m.planes[i].m.userptr = reinterpret_cast<unsigned long>(buf[i]);
                buffer.m.planes[i].length = len[i];
            }
        })) {
        ALOGERR("failed QBUF(%s, ptr[]=%p %p, len[0]=%d %d)", getBufTypeString(buftype), buf[0],
                buf[1], len[0], len[1]);
        return false;
    }

    return true;
}

bool LibScalerForJpeg::Device::queueBuffer(unsigned int buftype, int buf,
                                           unsigned int len[SCALER_MAX_PLANES]) {
    if (!queueBuffer(buftype, [buf, len](v4l2_buffer &buffer) {
            buffer.memory = V4L2_MEMORY_DMABUF;
            buffer.length = 1;
            buffer.m.planes[0].m.fd = buf;
            buffer.m.planes[0].length = len[0];
        })) {
        ALOGERR("failed QBUF(%s, fd=%d, len=%d", getBufTypeString(buftype), buf, len[0]);
        return false;
    }

    return true;
}

bool LibScalerForJpeg::Device::dequeueBuffer(unsigned int buftype, unsigned int memtype) {
    v4l2_buffer buffer{};
    v4l2_plane plane[SCALER_MAX_PLANES];

    memset(&plane, 0, sizeof(plane));

    buffer.type = buftype;
    buffer.memory = memtype;
    buffer.length = SCALER_MAX_PLANES;

    buffer.m.planes = plane;

    if (ioctl(mFd, VIDIOC_DQBUF, &buffer) < 0) {
        ALOGERR("failed DQBUF(%s)", getBufTypeString(buftype));
        return false;
    }

    return true;
}
