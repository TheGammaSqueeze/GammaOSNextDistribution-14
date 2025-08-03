// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2DecodeComponent"

#include <v4l2_codec2/v4l2/V4L2DecodeComponent.h>
#include <v4l2_codec2/v4l2/V4L2Decoder.h>

#include <base/bind.h>
#include <base/callback_helpers.h>

#include <cutils/properties.h>
#include <utils/Trace.h>

namespace android {

namespace {
// CCBC pauses sending input buffers to the component when all the output slots are filled by
// pending decoded buffers. If the available output buffers are exhausted before CCBC pauses sending
// input buffers, CCodec may timeout due to waiting for a available output buffer.
// This function returns the minimum number of output buffers to prevent the buffers from being
// exhausted before CCBC pauses sending input buffers.
size_t getMinNumOutputBuffers(VideoCodec codec) {
    // The constant values copied from CCodecBufferChannel.cpp.
    // (b/184020290): Check the value still sync when seeing error message from CCodec:
    // "previous call to queue exceeded timeout".
    constexpr size_t kSmoothnessFactor = 4;
    constexpr size_t kRenderingDepth = 3;
    // Extra number of needed output buffers for V4L2Decoder.
    constexpr size_t kExtraNumOutputBuffersForDecoder = 2;

    // The total needed number of output buffers at pipeline are:
    // - MediaCodec output slots: output delay + kSmoothnessFactor
    // - Surface: kRenderingDepth
    // - Component: kExtraNumOutputBuffersForDecoder
    return DecodeInterface::getOutputDelay(codec) + kSmoothnessFactor + kRenderingDepth +
           kExtraNumOutputBuffersForDecoder;
}
}  // namespace

// static
std::atomic<int32_t> V4L2DecodeComponent::sConcurrentInstances = 0;

// static
std::atomic<uint32_t> V4L2DecodeComponent::sNextDebugStreamId = 0;

// static
std::shared_ptr<C2Component> V4L2DecodeComponent::create(
        const std::string& name, c2_node_id_t id, std::shared_ptr<DecodeInterface> intfImpl,
        C2ComponentFactory::ComponentDeleter deleter) {
    static const int32_t kMaxConcurrentInstances =
            property_get_int32("ro.vendor.v4l2_codec2.decode_concurrent_instances", -1);
    static std::mutex mutex;

    std::lock_guard<std::mutex> lock(mutex);

    if (kMaxConcurrentInstances >= 0 && sConcurrentInstances.load() >= kMaxConcurrentInstances) {
        ALOGW("Reject to Initialize() due to too many instances: %d", sConcurrentInstances.load());
        return nullptr;
    } else if (sConcurrentInstances.load() == 0) {
        sNextDebugStreamId.store(0, std::memory_order_relaxed);
    }

    uint32_t debugStreamId = sNextDebugStreamId.fetch_add(1, std::memory_order_relaxed);
    return std::shared_ptr<C2Component>(
            new V4L2DecodeComponent(debugStreamId, name, id, std::move(intfImpl)), deleter);
}

V4L2DecodeComponent::V4L2DecodeComponent(uint32_t debugStreamId, const std::string& name,
                                         c2_node_id_t id, std::shared_ptr<DecodeInterface> intfImpl)
      : DecodeComponent(debugStreamId, name, id, intfImpl) {
    ALOGV("%s(): ", __func__);
    sConcurrentInstances.fetch_add(1, std::memory_order_relaxed);
}

V4L2DecodeComponent::~V4L2DecodeComponent() {
    ALOGV("%s(): ", __func__);
    sConcurrentInstances.fetch_sub(1, std::memory_order_relaxed);
}

void V4L2DecodeComponent::startTask(c2_status_t* status, ::base::WaitableEvent* done) {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    ::base::ScopedClosureRunner done_caller(
            ::base::BindOnce(&::base::WaitableEvent::Signal, ::base::Unretained(done)));
    *status = C2_CORRUPTED;

    const auto codec = mIntfImpl->getVideoCodec();
    if (!codec) {
        ALOGE("Failed to get video codec.");
        return;
    }
    const size_t inputBufferSize = mIntfImpl->getInputBufferSize();
    const size_t minNumOutputBuffers = getMinNumOutputBuffers(*codec);

    // ::base::Unretained(this) is safe here because |mDecoder| is always destroyed before
    // |mDecoderThread| is stopped, so |*this| is always valid during |mDecoder|'s lifetime.
    mDecoder = V4L2Decoder::Create(mDebugStreamId, *codec, inputBufferSize, minNumOutputBuffers,
                                   ::base::BindRepeating(&V4L2DecodeComponent::getVideoFramePool,
                                                         ::base::Unretained(this)),
                                   ::base::BindRepeating(&V4L2DecodeComponent::onOutputFrameReady,
                                                         ::base::Unretained(this)),
                                   ::base::BindRepeating(&V4L2DecodeComponent::reportError,
                                                         ::base::Unretained(this), C2_CORRUPTED),
                                   mDecoderTaskRunner, mIsSecure);
    if (!mDecoder) {
        ALOGE("Failed to create V4L2Decoder for %s", VideoCodecToString(*codec));
        return;
    }

    // Get default color aspects on start.
    if (!mIsSecure && *codec == VideoCodec::H264) {
        if (mIntfImpl->queryColorAspects(&mCurrentColorAspects) != C2_OK) return;
        mPendingColorAspectsChange = false;
    }

    *status = C2_OK;
}

}  // namespace android
