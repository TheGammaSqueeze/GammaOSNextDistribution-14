/*
 * Copyright 2022 The Android Open Source Project
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

#include <utils/Log.h>

#define DEBUG 0
#if DEBUG
#define DDD(...) ALOGD(__VA_ARGS__)
#else
#define DDD(...) ((void)0)
#endif

#include "MediaHevcDecoder.h"
#include "goldfish_media_utils.h"
#include <string.h>

MediaHevcDecoder::MediaHevcDecoder(RenderMode renderMode)
    : mRenderMode(renderMode) {
    if (renderMode == RenderMode::RENDER_BY_HOST_GPU) {
        mVersion = 200;
    } else if (renderMode == RenderMode::RENDER_BY_GUEST_CPU) {
        mVersion = 100;
    }
}

void MediaHevcDecoder::initHevcContext(unsigned int width, unsigned int height,
                                       unsigned int outWidth,
                                       unsigned int outHeight,
                                       PixelFormat pixFmt) {
    auto transport = GoldfishMediaTransport::getInstance();
    if (!mHasAddressSpaceMemory) {
        int slot = transport->getMemorySlot();
        if (slot < 0) {
            ALOGE("ERROR: Failed to initHevcContext: cannot get memory slot");
            return;
        }
        mSlot = slot;
        mAddressOffSet = static_cast<unsigned int>(mSlot) * (1 << 20);
        DDD("got memory lot %d addrr %x", mSlot, mAddressOffSet);
        mHasAddressSpaceMemory = true;
    }
    transport->writeParam(mVersion, 0, mAddressOffSet);
    transport->writeParam(width, 1, mAddressOffSet);
    transport->writeParam(height, 2, mAddressOffSet);
    transport->writeParam(outWidth, 3, mAddressOffSet);
    transport->writeParam(outHeight, 4, mAddressOffSet);
    transport->writeParam(static_cast<uint64_t>(pixFmt), 5, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec,
                             MediaOperation::InitContext, mAddressOffSet);
    auto *retptr = transport->getReturnAddr(mAddressOffSet);
    mHostHandle = *(uint64_t *)(retptr);
    DDD("initHevcContext: got handle to host %lld", mHostHandle);
}

void MediaHevcDecoder::resetHevcContext(unsigned int width, unsigned int height,
                                        unsigned int outWidth,
                                        unsigned int outHeight,
                                        PixelFormat pixFmt) {
    auto transport = GoldfishMediaTransport::getInstance();
    if (!mHasAddressSpaceMemory) {
        ALOGE("%s no address space memory", __func__);
        return;
    }
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->writeParam(width, 1, mAddressOffSet);
    transport->writeParam(height, 2, mAddressOffSet);
    transport->writeParam(outWidth, 3, mAddressOffSet);
    transport->writeParam(outHeight, 4, mAddressOffSet);
    transport->writeParam(static_cast<uint64_t>(pixFmt), 5, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec, MediaOperation::Reset,
                             mAddressOffSet);
    DDD("resetHevcContext: done");
}

void MediaHevcDecoder::destroyHevcContext() {

    DDD("return memory lot %d addrr %x", (int)(mAddressOffSet >> 23),
        mAddressOffSet);
    auto transport = GoldfishMediaTransport::getInstance();
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec,
                             MediaOperation::DestroyContext, mAddressOffSet);
    transport->returnMemorySlot(mSlot);
    mHasAddressSpaceMemory = false;
}

hevc_result_t MediaHevcDecoder::decodeFrame(uint8_t *img, size_t szBytes,
                                            uint64_t pts) {
    DDD("decode frame: use handle to host %lld", mHostHandle);
    hevc_result_t res = {0, 0};
    if (!mHasAddressSpaceMemory) {
        ALOGE("%s no address space memory", __func__);
        return res;
    }
    auto transport = GoldfishMediaTransport::getInstance();
    uint8_t *hostSrc = transport->getInputAddr(mAddressOffSet);
    if (img != nullptr && szBytes > 0) {
        memcpy(hostSrc, img, szBytes);
    }
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->writeParam(transport->offsetOf((uint64_t)(hostSrc)) -
                              mAddressOffSet,
                          1, mAddressOffSet);
    transport->writeParam((uint64_t)szBytes, 2, mAddressOffSet);
    transport->writeParam((uint64_t)pts, 3, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec,
                             MediaOperation::DecodeImage, mAddressOffSet);

    auto *retptr = transport->getReturnAddr(mAddressOffSet);
    res.bytesProcessed = *(uint64_t *)(retptr);
    res.ret = *(int *)(retptr + 8);

    return res;
}

void MediaHevcDecoder::sendMetadata(MetaDataColorAspects *ptr) {
    DDD("send metadata to host %p", ptr);
    if (!mHasAddressSpaceMemory) {
        ALOGE("%s no address space memory", __func__);
        return;
    }
    MetaDataColorAspects& meta = *ptr;
    auto transport = GoldfishMediaTransport::getInstance();
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->writeParam(meta.type, 1, mAddressOffSet);
    transport->writeParam(meta.primaries, 2, mAddressOffSet);
    transport->writeParam(meta.range, 3, mAddressOffSet);
    transport->writeParam(meta.transfer, 4, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec, MediaOperation::SendMetadata, mAddressOffSet);
}

void MediaHevcDecoder::flush() {
    if (!mHasAddressSpaceMemory) {
        ALOGE("%s no address space memory", __func__);
        return;
    }
    DDD("flush: use handle to host %lld", mHostHandle);
    auto transport = GoldfishMediaTransport::getInstance();
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec, MediaOperation::Flush,
                             mAddressOffSet);
}

hevc_image_t MediaHevcDecoder::getImage() {
    DDD("getImage: use handle to host %lld", mHostHandle);
    hevc_image_t res{};
    if (!mHasAddressSpaceMemory) {
        ALOGE("%s no address space memory", __func__);
        return res;
    }
    auto transport = GoldfishMediaTransport::getInstance();
    uint8_t *dst = transport->getInputAddr(
        mAddressOffSet); // Note: reuse the same addr for input and output
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->writeParam(transport->offsetOf((uint64_t)(dst)) - mAddressOffSet,
                          1, mAddressOffSet);
    transport->writeParam(-1, 2, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec,
                             MediaOperation::GetImage, mAddressOffSet);
    auto *retptr = transport->getReturnAddr(mAddressOffSet);
    res.ret = *(int *)(retptr);
    if (res.ret >= 0) {
        res.data = dst;
        res.width = *(uint32_t *)(retptr + 8);
        res.height = *(uint32_t *)(retptr + 16);
        res.pts = *(uint64_t *)(retptr + 24);
        res.color_primaries = *(uint32_t *)(retptr + 32);
        res.color_range = *(uint32_t *)(retptr + 40);
        res.color_trc = *(uint32_t *)(retptr + 48);
        res.colorspace = *(uint32_t *)(retptr + 56);
    } else if (res.ret == (int)(Err::DecoderRestarted)) {
        res.width = *(uint32_t *)(retptr + 8);
        res.height = *(uint32_t *)(retptr + 16);
    }
    return res;
}

hevc_image_t
MediaHevcDecoder::renderOnHostAndReturnImageMetadata(int hostColorBufferId) {
    DDD("%s: use handle to host %lld", __func__, mHostHandle);
    hevc_image_t res{};
    if (hostColorBufferId < 0) {
        ALOGE("%s negative color buffer id %d", __func__, hostColorBufferId);
        return res;
    }
    DDD("%s send color buffer id %d", __func__, hostColorBufferId);
    if (!mHasAddressSpaceMemory) {
        ALOGE("%s no address space memory", __func__);
        return res;
    }
    auto transport = GoldfishMediaTransport::getInstance();
    uint8_t *dst = transport->getInputAddr(
        mAddressOffSet); // Note: reuse the same addr for input and output
    transport->writeParam((uint64_t)mHostHandle, 0, mAddressOffSet);
    transport->writeParam(transport->offsetOf((uint64_t)(dst)) - mAddressOffSet,
                          1, mAddressOffSet);
    transport->writeParam((uint64_t)hostColorBufferId, 2, mAddressOffSet);
    transport->sendOperation(MediaCodecType::HevcCodec,
                             MediaOperation::GetImage, mAddressOffSet);
    auto *retptr = transport->getReturnAddr(mAddressOffSet);
    res.ret = *(int *)(retptr);
    if (res.ret >= 0) {
        res.data = dst; // note: the data could be junk
        res.width = *(uint32_t *)(retptr + 8);
        res.height = *(uint32_t *)(retptr + 16);
        res.pts = *(uint64_t *)(retptr + 24);
        res.color_primaries = *(uint32_t *)(retptr + 32);
        res.color_range = *(uint32_t *)(retptr + 40);
        res.color_trc = *(uint32_t *)(retptr + 48);
        res.colorspace = *(uint32_t *)(retptr + 56);
    } else if (res.ret == (int)(Err::DecoderRestarted)) {
        res.width = *(uint32_t *)(retptr + 8);
        res.height = *(uint32_t *)(retptr + 16);
    }
    return res;
}
