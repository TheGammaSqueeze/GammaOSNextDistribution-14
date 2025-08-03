#pragma once

#include "aidl/android/system/suspend/ISystemSuspend.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace system {
namespace suspend {
class BpSystemSuspend : public ::ndk::BpCInterface<ISystemSuspend> {
public:
  explicit BpSystemSuspend(const ::ndk::SpAIBinder& binder);
  virtual ~BpSystemSuspend();

  ::ndk::ScopedAStatus acquireWakeLock(::aidl::android::system::suspend::WakeLockType in_type, const std::string& in_name, std::shared_ptr<::aidl::android::system::suspend::IWakeLock>* _aidl_return) override;
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
