#pragma once

#include "aidl/android/hardware/health/IHealthInfoCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
class BpHealthInfoCallback : public ::ndk::BpCInterface<IHealthInfoCallback> {
public:
  explicit BpHealthInfoCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpHealthInfoCallback();

  ::ndk::ScopedAStatus healthInfoChanged(const ::aidl::android::hardware::health::HealthInfo& in_info) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
