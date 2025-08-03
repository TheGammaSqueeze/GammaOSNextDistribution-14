#pragma once

#include "aidl/android/hardware/gnss/IGnssNavigationMessageCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssNavigationMessageCallback : public ::ndk::BpCInterface<IGnssNavigationMessageCallback> {
public:
  explicit BpGnssNavigationMessageCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssNavigationMessageCallback();

  ::ndk::ScopedAStatus gnssNavigationMessageCb(const ::aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage& in_message) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
