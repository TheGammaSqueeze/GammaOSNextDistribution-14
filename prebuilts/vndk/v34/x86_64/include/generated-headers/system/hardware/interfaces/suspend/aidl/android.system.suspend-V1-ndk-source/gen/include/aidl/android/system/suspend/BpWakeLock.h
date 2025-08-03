#pragma once

#include "aidl/android/system/suspend/IWakeLock.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace system {
namespace suspend {
class BpWakeLock : public ::ndk::BpCInterface<IWakeLock> {
public:
  explicit BpWakeLock(const ::ndk::SpAIBinder& binder);
  virtual ~BpWakeLock();

  ::ndk::ScopedAStatus release() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
