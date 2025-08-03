/*
 * Copyright (C) 2019 Samsung Electronics Co.,LTD.
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
#ifndef __HARDWARE_EXYNOS_LIBSCALERFORJPEG_H__
#define __HARDWARE_EXYNOS_LIBSCALERFORJPEG_H__

#include <linux/videodev2.h>

#include <functional>

#include "ThumbnailScaler.h"

class LibScalerForJpeg : public ThumbnailScaler {
public:
    LibScalerForJpeg() {}
    ~LibScalerForJpeg() {}

    bool SetSrcImage(unsigned int width, unsigned int height, unsigned int v4l2_format) {
        return mSrcImage.set(width, height, v4l2_format);
    }

    bool SetDstImage(unsigned int width, unsigned int height, unsigned int v4l2_format) {
        return mDstImage.set(width, height, v4l2_format);
    }

    bool RunStream(int srcBuf[SCALER_MAX_PLANES], int srcLen[SCALER_MAX_PLANES], int dstBuf,
                   size_t dstLen);
    bool RunStream(char *srcBuf[SCALER_MAX_PLANES], int srcLen[SCALER_MAX_PLANES], int dstBuf,
                   size_t dstLen);

    bool available() { return mDevice.mFd >= 0; }

private:
    struct Device {
        int mFd;

        Device();
        ~Device();
        bool requestBuffers(unsigned int buftype, unsigned int memtype, unsigned int count);
        bool setFormat(unsigned int buftype, unsigned int format, unsigned int width,
                       unsigned int height, unsigned int planelen[SCALER_MAX_PLANES]);
        bool streamOn(unsigned int buftype);
        bool streamOff(unsigned int buftype);
        bool queueBuffer(unsigned int buftype, std::function<void(v4l2_buffer &)> bufferFiller);
        bool queueBuffer(unsigned int buftype, int buf[SCALER_MAX_PLANES],
                         unsigned int len[SCALER_MAX_PLANES]);
        bool queueBuffer(unsigned int buftype, char *buf[SCALER_MAX_PLANES],
                         unsigned int len[SCALER_MAX_PLANES]);
        bool queueBuffer(unsigned int buftype, int buf, unsigned int len[SCALER_MAX_PLANES]);
        bool dequeueBuffer(unsigned int buftype, unsigned int memtype);
    };

    struct Image {
        Device &mDevice;
        unsigned int width;
        unsigned int height;
        unsigned int format;
        unsigned int memoryType = 0;
        const unsigned int bufferType;
        unsigned int planeLen[SCALER_MAX_PLANES];

        Image(Device &dev, unsigned int w, unsigned int h, unsigned int f, unsigned int buftype)
              : mDevice(dev), width(w), height(h), format(f), bufferType(buftype) {}

        bool set(unsigned int width, unsigned int height, unsigned int format);
        bool begin(unsigned int memtype);
        bool cancelBuffer();

        template <class tBuf>
        bool queueBuffer(tBuf buf) {
            return mDevice.queueBuffer(bufferType, buf, planeLen);
        }
        bool dequeueBuffer() { return mDevice.dequeueBuffer(bufferType, memoryType); }

        bool same(unsigned int w, unsigned int h, unsigned int f) {
            return width == w && height == h && format == f;
        }
    };

    template <class T>
    bool queue(T srcBuf[SCALER_MAX_PLANES], int dstBuf) {
        if (!mSrcImage.queueBuffer(srcBuf)) return false;

        if (!mDstImage.queueBuffer(dstBuf)) {
            mSrcImage.cancelBuffer();
            return false;
        }

        if (!mSrcImage.dequeueBuffer() || !mDstImage.dequeueBuffer()) {
            mSrcImage.cancelBuffer();
            mDstImage.cancelBuffer();
            return false;
        }

        return true;
    }

    Device mDevice;
    Image mSrcImage{mDevice, 0, 0, V4L2_PIX_FMT_YUYV, V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE};
    Image mDstImage{mDevice, 0, 0, V4L2_PIX_FMT_YUYV, V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE};
};

#endif //__HARDWARE_EXYNOS_LIBSCALERFORJPEG_H__
