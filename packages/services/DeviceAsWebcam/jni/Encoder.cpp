/*
 * Copyright (C) 2023 The Android Open Source Project
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

//#define LOG_NDEBUG 0

#include "Encoder.h"

#include <DeviceAsWebcamNative.h>
#include <condition_variable>
#include <libyuv/convert.h>
#include <libyuv/convert_from.h>
#include <libyuv/rotate.h>
#include <log/log.h>
#include <queue>
#include <sched.h>

namespace android {
namespace webcam {

Encoder::Encoder(CameraConfig& config, EncoderCallback* cb)
    : mConfig(config), mCb(cb){
    // Inititalize intermediate buffers here.
    mI420.y = std::make_unique<uint8_t[]>(config.width * config.height);

    // TODO:(b/267794640): Can the size be width * height / 4 as it is subsampled by height
    //                     and width?
    mI420.u = std::make_unique<uint8_t[]>(config.width * config.height / 2);
    mI420.v = std::make_unique<uint8_t[]>(config.width * config.height / 2);

    mI420.yRowStride = config.width;
    mI420.uRowStride = config.width / 2;
    mI420.vRowStride = config.width / 2;

    if (mI420.y == nullptr || mI420.u == nullptr || mI420.v == nullptr) {
        ALOGE("%s Failed to allocate memory for intermediate I420 buffers", __FUNCTION__);
        return;
    }

    mInited = true;
}

bool Encoder::isInited() const {
    return mInited;
}

Encoder::~Encoder() {
    mContinueEncoding = false;
    if (mEncoderThread.joinable()) {
        mEncoderThread.join();
    }
}

void Encoder::encodeThreadLoop() {
    using namespace std::chrono_literals;
    ALOGV("%s Starting encode threadLoop", __FUNCTION__);
    EncodeRequest request;
    while (mContinueEncoding) {
        {
            std::unique_lock<std::mutex> l(mRequestLock);
            while (mRequestQueue.empty()) {
                mRequestCondition.wait_for(l, 50ms);
                if (!mContinueEncoding) {
                    return;
                }
            }
            request = mRequestQueue.front();
            mRequestQueue.pop();
        }
        encode(request);
    }

    // Thread signalled to exit.
    ALOGV("%s Encode thread now exiting", __FUNCTION__);
    std::unique_lock<std::mutex> l(mRequestLock);
    // Return any pending buffers with encode failure callbacks.
    while (!mRequestQueue.empty()) {
        request = mRequestQueue.front();
        mRequestQueue.pop();
        mCb->onEncoded(request.dstBuffer, request.srcBuffer, /*success*/ false);
    }
}

void Encoder::queueRequest(EncodeRequest& request) {
    std::unique_lock<std::mutex> l(mRequestLock);
    mRequestQueue.emplace(request);
    mRequestCondition.notify_one();
}

bool Encoder::checkError(const char* msg, j_common_ptr jpeg_error_info_) {
    if (jpeg_error_info_) {
        char err_buffer[JMSG_LENGTH_MAX];
        jpeg_error_info_->err->format_message(jpeg_error_info_, err_buffer);
        ALOGE("%s: %s: %s", __FUNCTION__, msg, err_buffer);
        jpeg_error_info_ = nullptr;
        return true;
    }

    return false;
}

uint32_t Encoder::i420ToJpeg(EncodeRequest& request) {
    ALOGV("%s: E cpu : %d", __FUNCTION__, sched_getcpu());
    j_common_ptr jpegErrorInfo;
    auto dstBuffer = request.dstBuffer;
    struct CustomJpegDestMgr : public jpeg_destination_mgr {
        JOCTET* buffer;
        size_t bufferSize;
        size_t encodedSize;
        bool success;
    } dmgr;

    // Set up error management
    jpegErrorInfo = nullptr;
    jpeg_error_mgr jErr;
    auto jpegCompressDeleter =
          [] (jpeg_compress_struct *c) {
              jpeg_destroy_compress(c);
              delete c;
          };

    std::unique_ptr<jpeg_compress_struct, decltype(jpegCompressDeleter)> cInfo(
            new jpeg_compress_struct(), jpegCompressDeleter);

    cInfo->err = jpeg_std_error(&jErr);
    cInfo->err->error_exit = [](j_common_ptr cInfo) {
        (*cInfo->err->output_message)(cInfo);
        if (cInfo->client_data) {
            auto& dmgr = *static_cast<CustomJpegDestMgr*>(cInfo->client_data);
            dmgr.success = false;
        }
    };

    jpeg_create_compress(cInfo.get());
    if (checkError("Error initializing compression", jpegErrorInfo)) {
        return 0;
    }

    dmgr.buffer = static_cast<JOCTET*>(dstBuffer->getMem());
    dmgr.bufferSize = dstBuffer->getLength();
    dmgr.encodedSize = 0;
    dmgr.success = true;
    cInfo->client_data = static_cast<void*>(&dmgr);
    dmgr.init_destination = [](j_compress_ptr cInfo) {
        auto& dmgr = static_cast<CustomJpegDestMgr&>(*cInfo->dest);
        dmgr.next_output_byte = dmgr.buffer;
        dmgr.free_in_buffer = dmgr.bufferSize;
    };

    dmgr.empty_output_buffer = [](j_compress_ptr) {
        ALOGE("%s:%d Out of buffer", __FUNCTION__, __LINE__);
        return 0;
    };

    dmgr.term_destination = [](j_compress_ptr cInfo) {
        auto& dmgr = static_cast<CustomJpegDestMgr&>(*cInfo->dest);
        dmgr.encodedSize = dmgr.bufferSize - dmgr.free_in_buffer;
        ALOGV("%s:%d Done with jpeg: %zu", __FUNCTION__, __LINE__, dmgr.encodedSize);
    };

    cInfo->dest = &dmgr;

    // Set up compression parameters
    cInfo->image_width = mConfig.width;
    cInfo->image_height = mConfig.height;
    cInfo->input_components = 3;
    cInfo->in_color_space = JCS_YCbCr;

    jpeg_set_defaults(cInfo.get());
    if (checkError("Error configuring defaults", jpegErrorInfo)) {
        return 0;
    }

    jpeg_set_colorspace(cInfo.get(), JCS_YCbCr);
    if (checkError("Error configuring color space", jpegErrorInfo)) {
        return 0;
    }

    cInfo->raw_data_in = 1;

    // YUV420 planar with chroma subsampling
    // Configure sampling factors. The sampling factor is JPEG subsampling 420
    // because the source format is YUV420. Note that libjpeg sampling factors
    // have a somewhat interesting meaning: Sampling of Y=2,U=1,V=1 means there is 1 U and
    // 1 V value for each 2 Y values */
    cInfo->comp_info[0].h_samp_factor = 2; // Y horizontal sampling
    cInfo->comp_info[0].v_samp_factor = 2; // Y vertical sampling
    cInfo->comp_info[1].h_samp_factor = 1; // U horizontal sampling
    cInfo->comp_info[1].v_samp_factor = 1; // U vertical sampling
    cInfo->comp_info[2].h_samp_factor = 1; // V horizontal sampling
    cInfo->comp_info[2].v_samp_factor = 1; // V vertical sampling

    // This vertical subsampling is the same for both Cb and Cr components as defined in
    // cInfo->comp_info.
    int cvSubSampling = cInfo->comp_info[0].v_samp_factor / cInfo->comp_info[1].v_samp_factor;

    // Start compression
    jpeg_start_compress(cInfo.get(), TRUE);
    if (checkError("Error starting compression", jpegErrorInfo)) {
        return 0;
    }

    // Compute our macroblock height, so we can pad our input to be vertically
    // macroblock aligned.
    int maxVSampFactor =
            std::max({cInfo->comp_info[0].v_samp_factor, cInfo->comp_info[1].v_samp_factor,
                      cInfo->comp_info[2].v_samp_factor});
    size_t mcuV = DCTSIZE * maxVSampFactor;
    size_t paddedHeight = mcuV * ((cInfo->image_height + mcuV - 1) / mcuV);

    std::vector<JSAMPROW> yLines(paddedHeight);
    std::vector<JSAMPROW> cbLines(paddedHeight / cvSubSampling);
    std::vector<JSAMPROW> crLines(paddedHeight / cvSubSampling);

    uint8_t* pY = mI420.y.get();
    uint8_t* pCr = mI420.v.get();
    uint8_t* pCb = mI420.u.get();

    uint32_t cbCrStride = mConfig.width / 2;
    uint32_t yStride = mConfig.width;

    for (uint32_t i = 0; i < paddedHeight; i++) {
        // Once we are in the padding territory we still point to the last line
        // effectively replicating it several times ~ CLAMP_TO_EDGE
        uint32_t li = std::min(i, cInfo->image_height - 1);
        yLines[i] = static_cast<JSAMPROW>(pY + li * yStride);
        if (i < paddedHeight / cvSubSampling) {
            li = std::min(i, (cInfo->image_height - 1) / cvSubSampling);
            crLines[i] = static_cast<JSAMPROW>(pCr + li * cbCrStride);
            cbLines[i] = static_cast<JSAMPROW>(pCb + li * cbCrStride);
        }
    }

    const uint32_t batchSize = DCTSIZE * maxVSampFactor;
    while (cInfo->next_scanline < cInfo->image_height) {
        JSAMPARRAY planes[3]{&yLines[cInfo->next_scanline],
                             &cbLines[cInfo->next_scanline / cvSubSampling],
                             &crLines[cInfo->next_scanline / cvSubSampling]};

        jpeg_write_raw_data(cInfo.get(), planes, batchSize);
        if (checkError("Error while compressing", jpegErrorInfo)) {
            return 0;
        }
    }

    jpeg_finish_compress(cInfo.get());
    if (checkError("Error while finishing compression", jpegErrorInfo)) {
        return 0;
    }

    ALOGV("%s: X", __FUNCTION__);
    return dmgr.encodedSize;
}

void Encoder::encodeToMJpeg(EncodeRequest& request) {
    // First fill intermediate I420 buffers
    // TODO(b/267794640) : Can we skip this conversion and encode to jpeg straight ?
    if (convertToI420(request) != 0) {
        ALOGE("%s: Encode from YUV_420 to I420 failed", __FUNCTION__);
        mCb->onEncoded(request.dstBuffer, request.srcBuffer, /*success*/ false);
        return;
    }

    // Now encode the I420 to JPEG
    uint32_t encodedSize = i420ToJpeg(request);
    if (encodedSize == 0) {
        ALOGE("%s: Encode from I420 to JPEG failed", __FUNCTION__);
        mCb->onEncoded(request.dstBuffer, request.srcBuffer, /*success*/ false);
        return;
    }
    request.dstBuffer->setBytesUsed(encodedSize);

    mCb->onEncoded(request.dstBuffer, request.srcBuffer, /*success*/ true);
}

int Encoder::convertToI420(EncodeRequest& request) {
    HardwareBufferDesc& src = request.srcBuffer;
    uint8_t* dstY = mI420.y.get();
    uint8_t* dstU = mI420.u.get();
    uint8_t* dstV = mI420.v.get();
    int32_t dstYRowStride = mConfig.width;
    int32_t dstURowStride = mConfig.width / 2;
    int32_t dstVRowStride = mConfig.width / 2;
    libyuv::RotationMode rotationMode = request.rotationDegrees == 180 ?
        libyuv::kRotate180 : libyuv::kRotate0;
    if (request.srcBuffer.format == AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM) {
        ARGBHardwareBufferDesc desc = std::get<ARGBHardwareBufferDesc>(src.bufferDesc);
        return libyuv::ARGBToI420(desc.buf, desc.rowStride, dstY,
                                  dstYRowStride, dstU, dstURowStride, dstV,
                                  dstVRowStride, mConfig.width, mConfig.height);
    }
    YuvHardwareBufferDesc desc = std::get<YuvHardwareBufferDesc>(src.bufferDesc);
    return libyuv::Android420ToI420Rotate(desc.yData, desc.yRowStride, desc.uData, desc.uRowStride,
                                    desc.vData, desc.vRowStride, desc.uvPixelStride, dstY,
                                    dstYRowStride, dstU, dstURowStride, dstV, dstVRowStride,
                                    mConfig.width, mConfig.height, rotationMode);
}

void Encoder::encodeToYUYV(EncodeRequest& r) {
    Buffer* dstBuffer = r.dstBuffer;
    // First convert from Android YUV format to I420.
    if (convertToI420(r) != 0) {
        ALOGE("%s: Encode from YUV_420 to I420 failed", __FUNCTION__);
        mCb->onEncoded(r.dstBuffer, r.srcBuffer, /*success*/ false);
        return;
    }

    int32_t dstYRowStride = mConfig.width;
    int32_t dstURowStride = mConfig.width / 2;
    int32_t dstVRowStride = mConfig.width / 2;
    uint8_t* dstY = mI420.y.get();
    uint8_t* dstU = mI420.u.get();
    uint8_t* dstV = mI420.v.get();

    // Now convert from I420 to YUYV
    if (libyuv::I420ToYUY2(dstY, dstYRowStride, dstU, dstURowStride, dstV, dstVRowStride,
                           reinterpret_cast<uint8_t*>(dstBuffer->getMem()),
                           /*rowStride*/ mConfig.width * 2, mConfig.width, mConfig.height) != 0) {
        ALOGE("%s: Encode from I420 to YUYV failed", __FUNCTION__);
        mCb->onEncoded(r.dstBuffer, r.srcBuffer, /*success*/ false);
        return;
    }
    dstBuffer->setBytesUsed(mConfig.width * mConfig.height * 2);
    // Call the callback
    mCb->onEncoded(r.dstBuffer, r.srcBuffer, /*success*/ true);
}

void Encoder::encode(EncodeRequest& encodeRequest) {
    // Based on the config format
    switch (mConfig.fcc) {
        case V4L2_PIX_FMT_YUYV:
            encodeToYUYV(encodeRequest);
            break;
        case V4L2_PIX_FMT_MJPEG:
            encodeToMJpeg(encodeRequest);
            break;
        default:
            ALOGE("%s: Fourcc %u not supported for encoding", __FUNCTION__, mConfig.fcc);
    }
}

void Encoder::startEncoderThread() {
    // mEncoderThread can call into java as a part of EncoderCallback
    mEncoderThread =
            DeviceAsWebcamNative::createJniAttachedThread(&Encoder::encodeThreadLoop, this);
    ALOGV("Started new Encoder Thread");
}

}  // namespace webcam
}  // namespace android
