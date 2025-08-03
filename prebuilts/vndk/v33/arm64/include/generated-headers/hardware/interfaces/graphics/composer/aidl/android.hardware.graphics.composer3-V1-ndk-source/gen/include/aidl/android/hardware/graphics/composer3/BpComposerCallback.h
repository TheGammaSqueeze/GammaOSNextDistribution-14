#pragma once

#include "aidl/android/hardware/graphics/composer3/IComposerCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class BpComposerCallback : public ::ndk::BpCInterface<IComposerCallback> {
public:
  explicit BpComposerCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpComposerCallback();

  ::ndk::ScopedAStatus onHotplug(int64_t in_display, bool in_connected) override;
  ::ndk::ScopedAStatus onRefresh(int64_t in_display) override;
  ::ndk::ScopedAStatus onSeamlessPossible(int64_t in_display) override;
  ::ndk::ScopedAStatus onVsync(int64_t in_display, int64_t in_timestamp, int32_t in_vsyncPeriodNanos) override;
  ::ndk::ScopedAStatus onVsyncPeriodTimingChanged(int64_t in_display, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline& in_updatedTimeline) override;
  ::ndk::ScopedAStatus onVsyncIdle(int64_t in_display) override;
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
