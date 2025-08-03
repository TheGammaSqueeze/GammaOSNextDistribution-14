#pragma once

#include "aidl/android/hardware/oemlock/IOemLock.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace oemlock {
class BpOemLock : public ::ndk::BpCInterface<IOemLock> {
public:
  explicit BpOemLock(const ::ndk::SpAIBinder& binder);
  virtual ~BpOemLock();

  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus isOemUnlockAllowedByCarrier(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isOemUnlockAllowedByDevice(bool* _aidl_return) override;
  ::ndk::ScopedAStatus setOemUnlockAllowedByCarrier(bool in_allowed, const std::vector<uint8_t>& in_signature, ::aidl::android::hardware::oemlock::OemLockSecureStatus* _aidl_return) override;
  ::ndk::ScopedAStatus setOemUnlockAllowedByDevice(bool in_allowed) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace oemlock
}  // namespace hardware
}  // namespace android
}  // namespace aidl
