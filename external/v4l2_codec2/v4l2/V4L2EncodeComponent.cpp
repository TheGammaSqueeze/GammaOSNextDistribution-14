// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2EncodeComponent"

#include <v4l2_codec2/v4l2/V4L2EncodeComponent.h>

#include <base/bind_helpers.h>

#include <cutils/properties.h>

#include <v4l2_codec2/components/BitstreamBuffer.h>
#include <v4l2_codec2/components/EncodeInterface.h>
#include <v4l2_codec2/v4l2/V4L2Encoder.h>

namespace android {

namespace {

// Check whether the specified |profile| is an H.264 profile.
bool IsH264Profile(C2Config::profile_t profile) {
    return (profile >= C2Config::PROFILE_AVC_BASELINE &&
            profile <= C2Config::PROFILE_AVC_ENHANCED_MULTIVIEW_DEPTH_HIGH);
}
}  // namespace

// static
std::atomic<int32_t> V4L2EncodeComponent::sConcurrentInstances = 0;

// static
std::shared_ptr<C2Component> V4L2EncodeComponent::create(
        C2String name, c2_node_id_t id, std::shared_ptr<EncodeInterface> intfImpl,
        C2ComponentFactory::ComponentDeleter deleter) {
    ALOGV("%s(%s)", __func__, name.c_str());

    static const int32_t kMaxConcurrentInstances =
            property_get_int32("ro.vendor.v4l2_codec2.encode_concurrent_instances", -1);

    static std::mutex mutex;
    std::lock_guard<std::mutex> lock(mutex);
    if (kMaxConcurrentInstances >= 0 && sConcurrentInstances.load() >= kMaxConcurrentInstances) {
        ALOGW("Cannot create additional encoder, maximum number of instances reached: %d",
              kMaxConcurrentInstances);
        return nullptr;
    }

    return std::shared_ptr<C2Component>(new V4L2EncodeComponent(name, id, std::move(intfImpl)),
                                        deleter);
}

V4L2EncodeComponent::V4L2EncodeComponent(C2String name, c2_node_id_t id,
                                         std::shared_ptr<EncodeInterface> interface)
      : EncodeComponent(name, id, interface) {
    ALOGV("%s():", __func__);
    sConcurrentInstances.fetch_add(1, std::memory_order_relaxed);
}

V4L2EncodeComponent::~V4L2EncodeComponent() {
    ALOGV("%s():", __func__);
    sConcurrentInstances.fetch_sub(1, std::memory_order_relaxed);
}

bool V4L2EncodeComponent::initializeEncoder() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mEncoderTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(!mInputFormatConverter);
    ALOG_ASSERT(!mEncoder);

    mLastFrameTime = std::nullopt;

    // Get the requested profile and level.
    C2Config::profile_t outputProfile = mInterface->getOutputProfile();

    // CSD only needs to be extracted when using an H.264 profile.
    mExtractCSD = IsH264Profile(outputProfile);

    std::optional<uint8_t> h264Level;
    if (IsH264Profile(outputProfile)) {
        h264Level = c2LevelToV4L2Level(mInterface->getOutputLevel());
    }

    // Get the stride used by the C2 framework, as this might be different from the stride used by
    // the V4L2 encoder.
    std::optional<uint32_t> stride =
            getVideoFrameStride(VideoEncoder::kInputPixelFormat, mInterface->getInputVisibleSize());
    if (!stride) {
        ALOGE("Failed to get video frame stride");
        reportError(C2_CORRUPTED);
        return false;
    }

    // Get the requested bitrate mode and bitrate. The C2 framework doesn't offer a parameter to
    // configure the peak bitrate, so we use a multiple of the target bitrate.
    mBitrateMode = mInterface->getBitrateMode();
    if (property_get_bool("persist.vendor.v4l2_codec2.disable_vbr", false)) {
        // NOTE: This is a workaround for b/235771157.
        ALOGW("VBR is disabled on this device");
        mBitrateMode = C2Config::BITRATE_CONST;
    }

    mBitrate = mInterface->getBitrate();

    mEncoder = V4L2Encoder::create(
            outputProfile, h264Level, mInterface->getInputVisibleSize(), *stride,
            mInterface->getKeyFramePeriod(), mBitrateMode, mBitrate,
            mBitrate * VideoEncoder::kPeakBitrateMultiplier,
            ::base::BindRepeating(&V4L2EncodeComponent::fetchOutputBlock, mWeakThis),
            ::base::BindRepeating(&V4L2EncodeComponent::onInputBufferDone, mWeakThis),
            ::base::BindRepeating(&V4L2EncodeComponent::onOutputBufferDone, mWeakThis),
            ::base::BindRepeating(&V4L2EncodeComponent::onDrainDone, mWeakThis),
            ::base::BindRepeating(&V4L2EncodeComponent::reportError, mWeakThis, C2_CORRUPTED),
            mEncoderTaskRunner);
    if (!mEncoder) {
        ALOGE("Failed to create V4L2Encoder (profile: %s)", profileToString(outputProfile));
        return false;
    }

    return true;
}

}  // namespace android