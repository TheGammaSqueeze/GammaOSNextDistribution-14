#pragma once

#include "aidl/android/hardware/memtrack/IMemtrack.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace memtrack {
class BpMemtrack : public ::ndk::BpCInterface<IMemtrack> {
public:
  explicit BpMemtrack(const ::ndk::SpAIBinder& binder);
  virtual ~BpMemtrack();

  ::ndk::ScopedAStatus getMemory(int32_t in_pid, ::aidl::android::hardware::memtrack::MemtrackType in_type, std::vector<::aidl::android::hardware::memtrack::MemtrackRecord>* _aidl_return) override;
  ::ndk::ScopedAStatus getGpuDeviceInfo(std::vector<::aidl::android::hardware::memtrack::DeviceInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace memtrack
}  // namespace hardware
}  // namespace android
}  // namespace aidl
