#pragma once

#include "aidl/android/hardware/graphics/composer3/IComposerClient.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class BpComposerClient : public ::ndk::BpCInterface<IComposerClient> {
public:
  explicit BpComposerClient(const ::ndk::SpAIBinder& binder);
  virtual ~BpComposerClient();

  ::ndk::ScopedAStatus createLayer(int64_t in_display, int32_t in_bufferSlotCount, int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus createVirtualDisplay(int32_t in_width, int32_t in_height, ::aidl::android::hardware::graphics::common::PixelFormat in_formatHint, int32_t in_outputBufferSlotCount, ::aidl::android::hardware::graphics::composer3::VirtualDisplay* _aidl_return) override;
  ::ndk::ScopedAStatus destroyLayer(int64_t in_display, int64_t in_layer) override;
  ::ndk::ScopedAStatus destroyVirtualDisplay(int64_t in_display) override;
  ::ndk::ScopedAStatus executeCommands(const std::vector<::aidl::android::hardware::graphics::composer3::DisplayCommand>& in_commands, std::vector<::aidl::android::hardware::graphics::composer3::CommandResultPayload>* _aidl_return) override;
  ::ndk::ScopedAStatus getActiveConfig(int64_t in_display, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getColorModes(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::ColorMode>* _aidl_return) override;
  ::ndk::ScopedAStatus getDataspaceSaturationMatrix(::aidl::android::hardware::graphics::common::Dataspace in_dataspace, std::vector<float>* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayAttribute(int64_t in_display, int32_t in_config, ::aidl::android::hardware::graphics::composer3::DisplayAttribute in_attribute, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayCapabilities(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::DisplayCapability>* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayConfigs(int64_t in_display, std::vector<int32_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayConnectionType(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayConnectionType* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayIdentificationData(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayIdentification* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayName(int64_t in_display, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayVsyncPeriod(int64_t in_display, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayedContentSample(int64_t in_display, int64_t in_maxFrames, int64_t in_timestamp, ::aidl::android::hardware::graphics::composer3::DisplayContentSample* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayedContentSamplingAttributes(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayContentSamplingAttributes* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayPhysicalOrientation(int64_t in_display, ::aidl::android::hardware::graphics::common::Transform* _aidl_return) override;
  ::ndk::ScopedAStatus getHdrCapabilities(int64_t in_display, ::aidl::android::hardware::graphics::composer3::HdrCapabilities* _aidl_return) override;
  ::ndk::ScopedAStatus getMaxVirtualDisplayCount(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getPerFrameMetadataKeys(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::PerFrameMetadataKey>* _aidl_return) override;
  ::ndk::ScopedAStatus getReadbackBufferAttributes(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ReadbackBufferAttributes* _aidl_return) override;
  ::ndk::ScopedAStatus getReadbackBufferFence(int64_t in_display, ::ndk::ScopedFileDescriptor* _aidl_return) override;
  ::ndk::ScopedAStatus getRenderIntents(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ColorMode in_mode, std::vector<::aidl::android::hardware::graphics::composer3::RenderIntent>* _aidl_return) override;
  ::ndk::ScopedAStatus getSupportedContentTypes(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::ContentType>* _aidl_return) override;
  ::ndk::ScopedAStatus getDisplayDecorationSupport(int64_t in_display, std::optional<::aidl::android::hardware::graphics::common::DisplayDecorationSupport>* _aidl_return) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::graphics::composer3::IComposerCallback>& in_callback) override;
  ::ndk::ScopedAStatus setActiveConfig(int64_t in_display, int32_t in_config) override;
  ::ndk::ScopedAStatus setActiveConfigWithConstraints(int64_t in_display, int32_t in_config, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeConstraints& in_vsyncPeriodChangeConstraints, ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline* _aidl_return) override;
  ::ndk::ScopedAStatus setBootDisplayConfig(int64_t in_display, int32_t in_config) override;
  ::ndk::ScopedAStatus clearBootDisplayConfig(int64_t in_display) override;
  ::ndk::ScopedAStatus getPreferredBootDisplayConfig(int64_t in_display, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus setAutoLowLatencyMode(int64_t in_display, bool in_on) override;
  ::ndk::ScopedAStatus setClientTargetSlotCount(int64_t in_display, int32_t in_clientTargetSlotCount) override;
  ::ndk::ScopedAStatus setColorMode(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ColorMode in_mode, ::aidl::android::hardware::graphics::composer3::RenderIntent in_intent) override;
  ::ndk::ScopedAStatus setContentType(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ContentType in_type) override;
  ::ndk::ScopedAStatus setDisplayedContentSamplingEnabled(int64_t in_display, bool in_enable, ::aidl::android::hardware::graphics::composer3::FormatColorComponent in_componentMask, int64_t in_maxFrames) override;
  ::ndk::ScopedAStatus setPowerMode(int64_t in_display, ::aidl::android::hardware::graphics::composer3::PowerMode in_mode) override;
  ::ndk::ScopedAStatus setReadbackBuffer(int64_t in_display, const ::aidl::android::hardware::common::NativeHandle& in_buffer, const ::ndk::ScopedFileDescriptor& in_releaseFence) override;
  ::ndk::ScopedAStatus setVsyncEnabled(int64_t in_display, bool in_enabled) override;
  ::ndk::ScopedAStatus setIdleTimerEnabled(int64_t in_display, int32_t in_timeoutMs) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
