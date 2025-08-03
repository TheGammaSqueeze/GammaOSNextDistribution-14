#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantCallback : public ::ndk::BpCInterface<ISupplicantCallback> {
public:
  explicit BpSupplicantCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantCallback();

  ::ndk::ScopedAStatus onInterfaceCreated(const std::string& in_ifaceName) override;
  ::ndk::ScopedAStatus onInterfaceRemoved(const std::string& in_ifaceName) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
