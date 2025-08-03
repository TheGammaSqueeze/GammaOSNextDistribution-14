#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/DisplayDecorationSupport.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#include <aidl/android/hardware/graphics/common/Transform.h>
#include <aidl/android/hardware/graphics/composer3/ColorMode.h>
#include <aidl/android/hardware/graphics/composer3/CommandResultPayload.h>
#include <aidl/android/hardware/graphics/composer3/ContentType.h>
#include <aidl/android/hardware/graphics/composer3/DisplayAttribute.h>
#include <aidl/android/hardware/graphics/composer3/DisplayCapability.h>
#include <aidl/android/hardware/graphics/composer3/DisplayCommand.h>
#include <aidl/android/hardware/graphics/composer3/DisplayConnectionType.h>
#include <aidl/android/hardware/graphics/composer3/DisplayContentSample.h>
#include <aidl/android/hardware/graphics/composer3/DisplayContentSamplingAttributes.h>
#include <aidl/android/hardware/graphics/composer3/DisplayIdentification.h>
#include <aidl/android/hardware/graphics/composer3/FormatColorComponent.h>
#include <aidl/android/hardware/graphics/composer3/HdrCapabilities.h>
#include <aidl/android/hardware/graphics/composer3/IComposerCallback.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadataKey.h>
#include <aidl/android/hardware/graphics/composer3/PowerMode.h>
#include <aidl/android/hardware/graphics/composer3/ReadbackBufferAttributes.h>
#include <aidl/android/hardware/graphics/composer3/RenderIntent.h>
#include <aidl/android/hardware/graphics/composer3/VirtualDisplay.h>
#include <aidl/android/hardware/graphics/composer3/VsyncPeriodChangeConstraints.h>
#include <aidl/android/hardware/graphics/composer3/VsyncPeriodChangeTimeline.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class IComposerClient : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IComposerClient();
  virtual ~IComposerClient();

  enum : int32_t { EX_BAD_CONFIG = 1 };
  enum : int32_t { EX_BAD_DISPLAY = 2 };
  enum : int32_t { EX_BAD_LAYER = 3 };
  enum : int32_t { EX_BAD_PARAMETER = 4 };
  enum : int32_t { EX_RESERVED = 5 };
  enum : int32_t { EX_NO_RESOURCES = 6 };
  enum : int32_t { EX_NOT_VALIDATED = 7 };
  enum : int32_t { EX_UNSUPPORTED = 8 };
  enum : int32_t { EX_SEAMLESS_NOT_ALLOWED = 9 };
  enum : int32_t { EX_SEAMLESS_NOT_POSSIBLE = 10 };
  enum : int32_t { INVALID_CONFIGURATION = 2147483647 };
  static const int32_t version = 1;
  static inline const std::string hash = "6e0e98f955d0bf3e7502a30fac0465e56cc98e75";
  static constexpr uint32_t TRANSACTION_createLayer = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_createVirtualDisplay = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_destroyLayer = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_destroyVirtualDisplay = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_executeCommands = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getActiveConfig = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getColorModes = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getDataspaceSaturationMatrix = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getDisplayAttribute = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getDisplayCapabilities = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getDisplayConfigs = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getDisplayConnectionType = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getDisplayIdentificationData = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getDisplayName = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getDisplayVsyncPeriod = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getDisplayedContentSample = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getDisplayedContentSamplingAttributes = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_getDisplayPhysicalOrientation = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_getHdrCapabilities = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_getMaxVirtualDisplayCount = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_getPerFrameMetadataKeys = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_getReadbackBufferAttributes = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_getReadbackBufferFence = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_getRenderIntents = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_getSupportedContentTypes = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_getDisplayDecorationSupport = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_setActiveConfig = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setActiveConfigWithConstraints = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setBootDisplayConfig = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_clearBootDisplayConfig = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_getPreferredBootDisplayConfig = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_setAutoLowLatencyMode = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_setClientTargetSlotCount = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_setColorMode = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_setContentType = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_setDisplayedContentSamplingEnabled = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_setPowerMode = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_setReadbackBuffer = FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_setVsyncEnabled = FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_setIdleTimerEnabled = FIRST_CALL_TRANSACTION + 40;

  static std::shared_ptr<IComposerClient> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IComposerClient>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IComposerClient>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IComposerClient>& impl);
  static const std::shared_ptr<IComposerClient>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus createLayer(int64_t in_display, int32_t in_bufferSlotCount, int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createVirtualDisplay(int32_t in_width, int32_t in_height, ::aidl::android::hardware::graphics::common::PixelFormat in_formatHint, int32_t in_outputBufferSlotCount, ::aidl::android::hardware::graphics::composer3::VirtualDisplay* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus destroyLayer(int64_t in_display, int64_t in_layer) = 0;
  virtual ::ndk::ScopedAStatus destroyVirtualDisplay(int64_t in_display) = 0;
  virtual ::ndk::ScopedAStatus executeCommands(const std::vector<::aidl::android::hardware::graphics::composer3::DisplayCommand>& in_commands, std::vector<::aidl::android::hardware::graphics::composer3::CommandResultPayload>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getActiveConfig(int64_t in_display, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getColorModes(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::ColorMode>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDataspaceSaturationMatrix(::aidl::android::hardware::graphics::common::Dataspace in_dataspace, std::vector<float>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayAttribute(int64_t in_display, int32_t in_config, ::aidl::android::hardware::graphics::composer3::DisplayAttribute in_attribute, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayCapabilities(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::DisplayCapability>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayConfigs(int64_t in_display, std::vector<int32_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayConnectionType(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayConnectionType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayIdentificationData(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayIdentification* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayName(int64_t in_display, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayVsyncPeriod(int64_t in_display, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayedContentSample(int64_t in_display, int64_t in_maxFrames, int64_t in_timestamp, ::aidl::android::hardware::graphics::composer3::DisplayContentSample* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayedContentSamplingAttributes(int64_t in_display, ::aidl::android::hardware::graphics::composer3::DisplayContentSamplingAttributes* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayPhysicalOrientation(int64_t in_display, ::aidl::android::hardware::graphics::common::Transform* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getHdrCapabilities(int64_t in_display, ::aidl::android::hardware::graphics::composer3::HdrCapabilities* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getMaxVirtualDisplayCount(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPerFrameMetadataKeys(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::PerFrameMetadataKey>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getReadbackBufferAttributes(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ReadbackBufferAttributes* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getReadbackBufferFence(int64_t in_display, ::ndk::ScopedFileDescriptor* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getRenderIntents(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ColorMode in_mode, std::vector<::aidl::android::hardware::graphics::composer3::RenderIntent>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSupportedContentTypes(int64_t in_display, std::vector<::aidl::android::hardware::graphics::composer3::ContentType>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDisplayDecorationSupport(int64_t in_display, std::optional<::aidl::android::hardware::graphics::common::DisplayDecorationSupport>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::graphics::composer3::IComposerCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus setActiveConfig(int64_t in_display, int32_t in_config) = 0;
  virtual ::ndk::ScopedAStatus setActiveConfigWithConstraints(int64_t in_display, int32_t in_config, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeConstraints& in_vsyncPeriodChangeConstraints, ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setBootDisplayConfig(int64_t in_display, int32_t in_config) = 0;
  virtual ::ndk::ScopedAStatus clearBootDisplayConfig(int64_t in_display) = 0;
  virtual ::ndk::ScopedAStatus getPreferredBootDisplayConfig(int64_t in_display, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setAutoLowLatencyMode(int64_t in_display, bool in_on) = 0;
  virtual ::ndk::ScopedAStatus setClientTargetSlotCount(int64_t in_display, int32_t in_clientTargetSlotCount) = 0;
  virtual ::ndk::ScopedAStatus setColorMode(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ColorMode in_mode, ::aidl::android::hardware::graphics::composer3::RenderIntent in_intent) = 0;
  virtual ::ndk::ScopedAStatus setContentType(int64_t in_display, ::aidl::android::hardware::graphics::composer3::ContentType in_type) = 0;
  virtual ::ndk::ScopedAStatus setDisplayedContentSamplingEnabled(int64_t in_display, bool in_enable, ::aidl::android::hardware::graphics::composer3::FormatColorComponent in_componentMask, int64_t in_maxFrames) = 0;
  virtual ::ndk::ScopedAStatus setPowerMode(int64_t in_display, ::aidl::android::hardware::graphics::composer3::PowerMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus setReadbackBuffer(int64_t in_display, const ::aidl::android::hardware::common::NativeHandle& in_buffer, const ::ndk::ScopedFileDescriptor& in_releaseFence) = 0;
  virtual ::ndk::ScopedAStatus setVsyncEnabled(int64_t in_display, bool in_enabled) = 0;
  virtual ::ndk::ScopedAStatus setIdleTimerEnabled(int64_t in_display, int32_t in_timeoutMs) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IComposerClient> default_impl;
};
class IComposerClientDefault : public IComposerClient {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
