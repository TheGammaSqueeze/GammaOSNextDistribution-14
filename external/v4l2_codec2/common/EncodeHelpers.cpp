// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "EncodeHelpers"

#include <v4l2_codec2/common/EncodeHelpers.h>

#include <linux/v4l2-controls.h>

#include <C2AllocatorGralloc.h>
#include <cutils/native_handle.h>
#include <ui/GraphicBuffer.h>
#include <utils/Log.h>

#include <v4l2_codec2/common/H264NalParser.h>

namespace android {

namespace {

// Android frameworks needs 4 bytes start code.
constexpr uint8_t kH264StartCode[] = {0x00, 0x00, 0x00, 0x01};
constexpr size_t kH264StartCodeSize = 4;

// Copy an H.264 NAL unit with size |srcSize| (without a start code) into a buffer with size
// |dstSize|. An H.264 start code is prepended to the NAL unit. After copying |dst| is adjusted to
// point to the address immediately following the copied data, and the |dstSize| is updated to
// reflect the remaining destination buffer size.
bool copyNALUPrependingStartCode(const uint8_t* src, size_t srcSize, uint8_t** dst,
                                 size_t* dstSize) {
    size_t naluSize = srcSize + kH264StartCodeSize;
    if (naluSize > *dstSize) {
        ALOGE("Couldn't copy NAL unit, not enough space in destination buffer");
        return false;
    }
    memcpy(*dst, kH264StartCode, kH264StartCodeSize);
    memcpy(*dst + kH264StartCodeSize, src, srcSize);
    *dst += naluSize;
    *dstSize -= naluSize;
    return true;
}

}  // namespace

uint8_t c2LevelToV4L2Level(C2Config::level_t level) {
    switch (level) {
    case C2Config::LEVEL_AVC_1:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_0;
    case C2Config::LEVEL_AVC_1B:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1B;
    case C2Config::LEVEL_AVC_1_1:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_1;
    case C2Config::LEVEL_AVC_1_2:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_2;
    case C2Config::LEVEL_AVC_1_3:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_3;
    case C2Config::LEVEL_AVC_2:
        return V4L2_MPEG_VIDEO_H264_LEVEL_2_0;
    case C2Config::LEVEL_AVC_2_1:
        return V4L2_MPEG_VIDEO_H264_LEVEL_2_1;
    case C2Config::LEVEL_AVC_2_2:
        return V4L2_MPEG_VIDEO_H264_LEVEL_2_2;
    case C2Config::LEVEL_AVC_3:
        return V4L2_MPEG_VIDEO_H264_LEVEL_3_0;
    case C2Config::LEVEL_AVC_3_1:
        return V4L2_MPEG_VIDEO_H264_LEVEL_3_1;
    case C2Config::LEVEL_AVC_3_2:
        return V4L2_MPEG_VIDEO_H264_LEVEL_3_2;
    case C2Config::LEVEL_AVC_4:
        return V4L2_MPEG_VIDEO_H264_LEVEL_4_0;
    case C2Config::LEVEL_AVC_4_1:
        return V4L2_MPEG_VIDEO_H264_LEVEL_4_1;
    case C2Config::LEVEL_AVC_4_2:
        return V4L2_MPEG_VIDEO_H264_LEVEL_4_2;
    case C2Config::LEVEL_AVC_5:
        return V4L2_MPEG_VIDEO_H264_LEVEL_5_0;
    case C2Config::LEVEL_AVC_5_1:
        return V4L2_MPEG_VIDEO_H264_LEVEL_5_1;
    default:
        ALOGE("Unrecognizable C2 level (value = 0x%x)...", level);
        return 0;
    }
}

android_ycbcr getGraphicBlockInfo(const C2ConstGraphicBlock& block) {
    uint32_t width, height, format, stride, igbp_slot, generation;
    uint64_t usage, igbp_id;
    android::_UnwrapNativeCodec2GrallocMetadata(block.handle(), &width, &height, &format, &usage,
                                                &stride, &generation, &igbp_id, &igbp_slot);
    native_handle_t* grallocHandle = android::UnwrapNativeCodec2GrallocHandle(block.handle());
    sp<GraphicBuffer> buf = new GraphicBuffer(grallocHandle, GraphicBuffer::CLONE_HANDLE, width,
                                              height, format, 1, usage, stride);
    native_handle_delete(grallocHandle);

    // Pass SW flag so that ARCVM returns the guest buffer dimensions instead
    // of the host buffer dimensions. This means we will have to convert the
    // return value from ptrs to buffer offsets ourselves.
    android_ycbcr ycbcr = {};
    int32_t status = buf->lockYCbCr(GRALLOC_USAGE_SW_READ_OFTEN, &ycbcr);
    if (status != OK) ALOGE("lockYCbCr is failed: %d", (int)status);
    buf->unlock();

    uintptr_t y = reinterpret_cast<uintptr_t>(ycbcr.y);
    ycbcr.y = nullptr;
    ycbcr.cb = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(ycbcr.cb) - y);
    ycbcr.cr = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(ycbcr.cr) - y);

    return ycbcr;
}

bool extractSPSPPS(const uint8_t* data, size_t length, std::vector<uint8_t>* sps,
                   std::vector<uint8_t>* pps) {
    bool foundSPS = false;
    bool foundPPS = false;
    H264NalParser parser(data, length);
    while (!(foundSPS && foundPPS) && parser.locateNextNal()) {
        switch (parser.type()) {
        case H264NalParser::kSPSType:
            sps->resize(parser.length());
            memcpy(sps->data(), parser.data(), parser.length());
            foundSPS = true;
            break;
        case H264NalParser::kPPSType:
            pps->resize(parser.length());
            memcpy(pps->data(), parser.data(), parser.length());
            foundPPS = true;
            break;
        }
    }
    return foundSPS && foundPPS;
}

bool extractCSDInfo(std::unique_ptr<C2StreamInitDataInfo::output>* const csd, const uint8_t* data,
                    size_t length) {
    csd->reset();

    std::vector<uint8_t> sps;
    std::vector<uint8_t> pps;
    if (!extractSPSPPS(data, length, &sps, &pps)) {
        return false;
    }

    size_t configDataLength = sps.size() + pps.size() + (2u * kH264StartCodeSize);
    ALOGV("Extracted codec config data: length=%zu", configDataLength);

    *csd = C2StreamInitDataInfo::output::AllocUnique(configDataLength, 0u);
    uint8_t* csdBuffer = (*csd)->m.value;
    return copyNALUPrependingStartCode(sps.data(), sps.size(), &csdBuffer, &configDataLength) &&
           copyNALUPrependingStartCode(pps.data(), pps.size(), &csdBuffer, &configDataLength);
}

size_t prependSPSPPSToIDR(const uint8_t* src, size_t srcSize, uint8_t* dst, size_t dstSize,
                          std::vector<uint8_t>* sps, std::vector<uint8_t>* pps) {
    bool foundStreamParams = false;
    size_t remainingDstSize = dstSize;
    H264NalParser parser(src, srcSize);
    while (parser.locateNextNal()) {
        switch (parser.type()) {
        case H264NalParser::kSPSType:
            // SPS found, copy to cache.
            ALOGV("Found SPS (length %zu)", parser.length());
            sps->resize(parser.length());
            memcpy(sps->data(), parser.data(), parser.length());
            foundStreamParams = true;
            break;
        case H264NalParser::kPPSType:
            // PPS found, copy to cache.
            ALOGV("Found PPS (length %zu)", parser.length());
            pps->resize(parser.length());
            memcpy(pps->data(), parser.data(), parser.length());
            foundStreamParams = true;
            break;
        case H264NalParser::kIDRType:
            ALOGV("Found IDR (length %zu)", parser.length());
            if (foundStreamParams) {
                ALOGV("Not injecting SPS and PPS before IDR, already present");
                break;
            }

            // Prepend the cached SPS and PPS to the IDR NAL unit.
            if (sps->empty() || pps->empty()) {
                ALOGE("No cached SPS or PPS NAL unit available to inject before IDR");
                return 0;
            }
            if (!copyNALUPrependingStartCode(sps->data(), sps->size(), &dst, &remainingDstSize)) {
                ALOGE("Not enough space to inject SPS NAL unit before IDR");
                return 0;
            }
            if (!copyNALUPrependingStartCode(pps->data(), pps->size(), &dst, &remainingDstSize)) {
                ALOGE("Not enough space to inject PPS NAL unit before IDR");
                return 0;
            }

            ALOGV("Stream header injected before IDR");
            break;
        }

        // Copy the NAL unit to the new output buffer.
        if (!copyNALUPrependingStartCode(parser.data(), parser.length(), &dst, &remainingDstSize)) {
            ALOGE("NAL unit does not fit in the provided output buffer");
            return 0;
        }
    }

    return dstSize - remainingDstSize;
}

}  // namespace android
