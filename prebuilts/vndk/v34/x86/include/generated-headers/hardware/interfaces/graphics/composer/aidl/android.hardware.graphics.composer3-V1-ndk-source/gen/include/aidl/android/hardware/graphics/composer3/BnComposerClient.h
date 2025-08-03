#pragma once

#include "aidl/android/hardware/graphics/composer3/IComposerClient.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class BnComposerClient : public ::ndk::BnCInterface<IComposerClient> {
public:
  BnComposerClient();
  virtual ~BnComposerClient();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IComposerClientDelegator : public BnComposerClient {
public:
  explicit IComposerClientDelegator(const std::shared_ptr<IComposerClient> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IComposerClient::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus createLayer(int64_t in_display, int32_t in_bufferSlotCount, int64_t* _aidl_return) override {
    return _impl->createLayer(in_display, in_bufferSlotCount, _aidl_return);
  }
  ::ndk::ScopedAStatus createVirtualDisplay(int32_t in_width, int32_t in_height, ::aidl::android::hardware::graphics::common::PixelFormat in_formatHint, int32_t in_outputBufferSlotCount, ::aidl::android::hardware::graphics::composer3::VirtualDisplay* _aidl_return) override {
    return _impl->createVirtualDisplay(in_width, in_height, in_formatHint, in_outputBufferSlotCount, _aidl_return);
  }
  ::ndk::ScopedAStatus destroyLayer(int64_t in_display, int64_t in_layer) override {
    return _impl->destroyLayer(in_display, in_layer);
  }
  ::ndk::ScopedAStatus destroyVirtualDisplay(int64_t in_display) override {
    return _impl->destroyVirtualDisplay(in_display);
  }
  ::ndk::ScopedAStatus executeCommands(const std::vector<::aidl::android::hardware::graphics::composer3::DisplayCommand>& in_commands, std::vector<::aidl::android::hardware::graphics::composer3::CommandResultPayload>* _aidl_return) override {
    return _impl->executeCommands(in_commands, _aidl_return);
  }
  ::ndk::ScopedAStatus getActiveConfig(int64_t in_display, int32_t* _aidl_return) override {
    return _impl->getActiveConfig(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getColorModes(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::ColorMode>* _aidl_return) override {
    return _impl->getColorModes(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDataspaceSaturationMatrix(::aidl::android::hardware::graphics::common::Dataspace in_dataspace, std::vector<float>* _aidl_return) override {
    return _impl->getDataspaceSaturationMatrix(in_dataspace, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayAttribute(int64_t in_display, int32_t in_config, ::aidl::android::hardware::graphics::composer3::DisplayAttribute in_attribute, int32_t* _aidl_return) override {
    return _impl->getDisplayAttribute(in_display, in_config, in_attribute, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayCapabilities(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::DisplayCapability>* _aidl_return) override {
    return _impl->getDisplayCapabilities(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayConfigs(int64_t in_display, std::vector<int32_t>* _aidl_return) override {
    return _impl->getDisplayConfigs(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayConnectionType(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayConnectionType* _aidl_return) override {
    return _impl->getDisplayConnectionType(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayIdentificationData(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayIdentification* _aidl_return) override {
    return _impl->getDisplayIdentificationData(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayName(int64_t in_display, std::string* _aidl_return) override {
    return _impl->getDisplayName(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayVsyncPeriod(int64_t in_display, int32_t* _aidl_return) override {
    return _impl->getDisplayVsyncPeriod(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayedContentSample(int64_t in_display, int64_t in_maxFrames, int64_t in_timestamp, ::aidl::android::hardware::graphics::composer3::DisplayContentSample* _aidl_return) override {
    return _impl->getDisplayedContentSample(in_display, in_maxFrames, in_timestamp, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayedContentSamplingAttributes(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayContentSamplingAttributes* _aidl_return) override {
    return _impl->getDisplayedContentSamplingAttributes(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayPhysicalOrientation(int64_t in_display, ::aidl::android::hardware::graphics::common::Transform* _aidl_return) override {
    return _impl->getDisplayPhysicalOrientation(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getHdrCapabilities(int64_t in_display, ::aidl::android::hardware::graphics::composer3::HdrCapabilities* _aidl_return) override {
    return _impl->getHdrCapabilities(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getMaxVirtualDisplayCount(int32_t* _aidl_return) override {
    return _impl->getMaxVirtualDisplayCount(_aidl_return);
  }
  ::ndk::ScopedAStatus getPerFrameMetadataKeys(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::PerFrameMetadataKey>* _aidl_return) override {
    return _impl->getPerFrameMetadataKeys(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getReadbackBufferAttributes(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ReadbackBufferAttributes* _aidl_return) override {
    return _impl->getReadbackBufferAttributes(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getReadbackBufferFence(int64_t in_display, ::ndk::ScopedFileDescriptor* _aidl_return) override {
    return _impl->getReadbackBufferFence(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getRenderIntents(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ColorMode in_mode, std::vector<::aidl::android::hardware::graphics::composer3::RenderIntent>* _aidl_return) override {
    return _impl->getRenderIntents(in_display, in_mode, _aidl_return);
  }
  ::ndk::ScopedAStatus getSupportedContentTypes(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::ContentType>* _aidl_return) override {
    return _impl->getSupportedContentTypes(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus getDisplayDecorationSupport(int64_t in_display, std::optional<::aidl::android::hardware::graphics::common::DisplayDecorationSupport>* _aidl_return) override {
    return _impl->getDisplayDecorationSupport(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::graphics::composer3::IComposerCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus setActiveConfig(int64_t in_display, int32_t in_config) override {
    return _impl->setActiveConfig(in_display, in_config);
  }
  ::ndk::ScopedAStatus setActiveConfigWithConstraints(int64_t in_display, int32_t in_config, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeConstraints& in_vsyncPeriodChangeConstraints, ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline* _aidl_return) override {
    return _impl->setActiveConfigWithConstraints(in_display, in_config, in_vsyncPeriodChangeConstraints, _aidl_return);
  }
  ::ndk::ScopedAStatus setBootDisplayConfig(int64_t in_display, int32_t in_config) override {
    return _impl->setBootDisplayConfig(in_display, in_config);
  }
  ::ndk::ScopedAStatus clearBootDisplayConfig(int64_t in_display) override {
    return _impl->clearBootDisplayConfig(in_display);
  }
  ::ndk::ScopedAStatus getPreferredBootDisplayConfig(int64_t in_display, int32_t* _aidl_return) override {
    return _impl->getPreferredBootDisplayConfig(in_display, _aidl_return);
  }
  ::ndk::ScopedAStatus setAutoLowLatencyMode(int64_t in_display, bool in_on) override {
    return _impl->setAutoLowLatencyMode(in_display, in_on);
  }
  ::ndk::ScopedAStatus setClientTargetSlotCount(int64_t in_display, int32_t in_clientTargetSlotCount) override {
    return _impl->setClientTargetSlotCount(in_display, in_clientTargetSlotCount);
  }
  ::ndk::ScopedAStatus setColorMode(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ColorMode in_mode, ::aidl::android::hardware::graphics::composer3::RenderIntent in_intent) override {
    return _impl->setColorMode(in_display, in_mode, in_intent);
  }
  ::ndk::ScopedAStatus setContentType(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ContentType in_type) override {
    return _impl->setContentType(in_display, in_type);
  }
  ::ndk::ScopedAStatus setDisplayedContentSamplingEnabled(int64_t in_display, bool in_enable, ::aidl::android::hardware::graphics::composer3::FormatColorComponent in_componentMask, int64_t in_maxFrames) override {
    return _impl->setDisplayedContentSamplingEnabled(in_display, in_enable, in_componentMask, in_maxFrames);
  }
  ::ndk::ScopedAStatus setPowerMode(int64_t in_display, ::aidl::android::hardware::graphics::composer3::PowerMode in_mode) override {
    return _impl->setPowerMode(in_display, in_mode);
  }
  ::ndk::ScopedAStatus setReadbackBuffer(int64_t in_display, const ::aidl::android::hardware::common::NativeHandle& in_buffer, const ::ndk::ScopedFileDescriptor& in_releaseFence) override {
    return _impl->setReadbackBuffer(in_display, in_buffer, in_releaseFence);
  }
  ::ndk::ScopedAStatus setVsyncEnabled(int64_t in_display, bool in_enabled) override {
    return _impl->setVsyncEnabled(in_display, in_enabled);
  }
  ::ndk::ScopedAStatus setIdleTimerEnabled(int64_t in_display, int32_t in_timeoutMs) override {
    return _impl->setIdleTimerEnabled(in_display, in_timeoutMs);
  }
protected:
private:
  std::shared_ptr<IComposerClient> _impl;
};

}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
