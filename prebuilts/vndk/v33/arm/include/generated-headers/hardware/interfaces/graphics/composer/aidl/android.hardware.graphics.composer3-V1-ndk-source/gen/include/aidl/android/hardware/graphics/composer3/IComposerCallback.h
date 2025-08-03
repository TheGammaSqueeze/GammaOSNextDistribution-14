#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/graphics/composer3/VsyncPeriodChangeTimeline.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class IComposerCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IComposerCallback();
  virtual ~IComposerCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "6e0e98f955d0bf3e7502a30fac0465e56cc98e75";
  static constexpr uint32_t TRANSACTION_onHotplug = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onRefresh = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onSeamlessPossible = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onVsync = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onVsyncPeriodTimingChanged = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_onVsyncIdle = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IComposerCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IComposerCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IComposerCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IComposerCallback>& impl);
  static const std::shared_ptr<IComposerCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onHotplug(int64_t in_display, bool in_connected) = 0;
  virtual ::ndk::ScopedAStatus onRefresh(int64_t in_display) = 0;
  virtual ::ndk::ScopedAStatus onSeamlessPossible(int64_t in_display) = 0;
  virtual ::ndk::ScopedAStatus onVsync(int64_t in_display, int64_t in_timestamp, int32_t in_vsyncPeriodNanos) = 0;
  virtual ::ndk::ScopedAStatus onVsyncPeriodTimingChanged(int64_t in_display, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline& in_updatedTimeline) = 0;
  virtual ::ndk::ScopedAStatus onVsyncIdle(int64_t in_display) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IComposerCallback> default_impl;
};
class IComposerCallbackDefault : public IComposerCallback {
public:
  ::ndk::ScopedAStatus onHotplug(int64_t in_display, bool in_connected) override;
  ::ndk::ScopedAStatus onRefresh(int64_t in_display) override;
  ::ndk::ScopedAStatus onSeamlessPossible(int64_t in_display) override;
  ::ndk::ScopedAStatus onVsync(int64_t in_display, int64_t in_timestamp, int32_t in_vsyncPeriodNanos) override;
  ::ndk::ScopedAStatus onVsyncPeriodTimingChanged(int64_t in_display, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline& in_updatedTimeline) override;
  ::ndk::ScopedAStatus onVsyncIdle(int64_t in_display) override;
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
