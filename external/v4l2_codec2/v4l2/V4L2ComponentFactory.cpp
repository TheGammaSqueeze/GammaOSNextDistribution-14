// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2ComponentFactory"

#include <v4l2_codec2/v4l2/V4L2ComponentFactory.h>

#include <codec2/hidl/1.0/InputBufferManager.h>
#include <log/log.h>

#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/components/DecodeInterface.h>
#include <v4l2_codec2/components/EncodeInterface.h>
#include <v4l2_codec2/v4l2/V4L2ComponentCommon.h>
#include <v4l2_codec2/v4l2/V4L2DecodeComponent.h>
#include <v4l2_codec2/v4l2/V4L2Device.h>
#include <v4l2_codec2/v4l2/V4L2EncodeComponent.h>

namespace android {

// static
std::unique_ptr<V4L2ComponentFactory> V4L2ComponentFactory::create(
        const std::string& componentName, std::shared_ptr<C2ReflectorHelper> reflector) {
    ALOGV("%s(%s)", __func__, componentName.c_str());

    if (!android::V4L2ComponentName::isValid(componentName.c_str())) {
        ALOGE("Invalid component name: %s", componentName.c_str());
        return nullptr;
    }
    if (reflector == nullptr) {
        ALOGE("reflector is null");
        return nullptr;
    }

    bool isEncoder = android::V4L2ComponentName::isEncoder(componentName.c_str());
    return std::make_unique<V4L2ComponentFactory>(componentName, isEncoder, std::move(reflector));
}

V4L2ComponentFactory::V4L2ComponentFactory(const std::string& componentName, bool isEncoder,
                                           std::shared_ptr<C2ReflectorHelper> reflector)
      : mComponentName(componentName), mIsEncoder(isEncoder), mReflector(std::move(reflector)) {
    using namespace ::android::hardware::media::c2::V1_0;
    // To minimize IPC, we generally want the codec2 framework to release and
    // recycle input buffers when the corresponding work item is done. However,
    // sometimes it is necessary to provide more input to unblock a decoder.
    //
    // Optimally we would configure this on a per-context basis. However, the
    // InputBufferManager is a process-wide singleton, so we need to configure it
    // pessimistically. Basing the interval on frame timing can be suboptimal if
    // the decoded output isn't being displayed, but that's not a primary use case
    // and few videos will actually rely on this behavior.
    constexpr nsecs_t kMinFrameIntervalNs = 1000000000ull / 60;
    uint32_t delayCount = 0;
    for (auto c : kAllCodecs) {
        delayCount = std::max(delayCount, DecodeInterface::getOutputDelay(c));
    }
    utils::InputBufferManager::setNotificationInterval(delayCount * kMinFrameIntervalNs / 2);
}

c2_status_t V4L2ComponentFactory::createComponent(c2_node_id_t id,
                                                  std::shared_ptr<C2Component>* const component,
                                                  ComponentDeleter deleter) {
    ALOGV("%s(%d), componentName: %s, isEncoder: %d", __func__, id, mComponentName.c_str(),
          mIsEncoder);

    if (mReflector == nullptr) {
        ALOGE("mReflector doesn't exist.");
        return C2_CORRUPTED;
    }

    if (mIsEncoder) {
        std::shared_ptr<EncodeInterface> intfImpl;
        c2_status_t status = createEncodeInterface(&intfImpl);
        if (status != C2_OK) {
            return status;
        }

        *component = V4L2EncodeComponent::create(mComponentName, id, std::move(intfImpl), deleter);
    } else {
        std::shared_ptr<DecodeInterface> intfImpl;
        c2_status_t status = createDecodeInterface(&intfImpl);
        if (status != C2_OK) {
            return status;
        }

        *component = V4L2DecodeComponent::create(mComponentName, id, std::move(intfImpl), deleter);
    }
    return *component ? C2_OK : C2_NO_MEMORY;
}

c2_status_t V4L2ComponentFactory::createInterface(
        c2_node_id_t id, std::shared_ptr<C2ComponentInterface>* const interface,
        InterfaceDeleter deleter) {
    ALOGV("%s(), componentName: %s", __func__, mComponentName.c_str());

    if (mReflector == nullptr) {
        ALOGE("mReflector doesn't exist.");
        return C2_CORRUPTED;
    }

    if (mIsEncoder) {
        std::shared_ptr<EncodeInterface> intfImpl;
        c2_status_t status = createEncodeInterface(&intfImpl);
        if (status != C2_OK) {
            return status;
        }

        *interface = std::shared_ptr<C2ComponentInterface>(
                new SimpleInterface<EncodeInterface>(mComponentName.c_str(), id,
                                                     std::move(intfImpl)),
                deleter);
        return C2_OK;
    } else {
        std::shared_ptr<DecodeInterface> intfImpl;
        c2_status_t status = createDecodeInterface(&intfImpl);
        if (status != C2_OK) {
            return status;
        }

        *interface = std::shared_ptr<C2ComponentInterface>(
                new SimpleInterface<DecodeInterface>(mComponentName.c_str(), id,
                                                     std::move(intfImpl)),
                deleter);
        return C2_OK;
    }
}

c2_status_t V4L2ComponentFactory::createEncodeInterface(
        std::shared_ptr<EncodeInterface>* intfImpl) {
    if (!mCapabilites) {
        auto codec = V4L2ComponentName::getCodec(mComponentName);
        if (!codec) {
            return C2_CORRUPTED;
        }
        mCapabilites = std::make_unique<SupportedCapabilities>(
                V4L2Device::queryEncodingCapabilities(*codec));
    }

    *intfImpl = std::make_shared<EncodeInterface>(mComponentName, mReflector, *mCapabilites);
    if (*intfImpl == nullptr) {
        return C2_NO_MEMORY;
    }

    return (*intfImpl)->status();
}

c2_status_t V4L2ComponentFactory::createDecodeInterface(
        std::shared_ptr<DecodeInterface>* intfImpl) {
    if (!mCapabilites) {
        auto codec = V4L2ComponentName::getCodec(mComponentName);
        if (!codec) {
            return C2_CORRUPTED;
        }
        mCapabilites = std::make_unique<SupportedCapabilities>(
                V4L2Device::queryDecodingCapabilities(*codec));
    }

    *intfImpl = std::make_shared<DecodeInterface>(mComponentName, mReflector, *mCapabilites);
    if (*intfImpl == nullptr) {
        return C2_NO_MEMORY;
    }

    return (*intfImpl)->status();
}

}  // namespace android
