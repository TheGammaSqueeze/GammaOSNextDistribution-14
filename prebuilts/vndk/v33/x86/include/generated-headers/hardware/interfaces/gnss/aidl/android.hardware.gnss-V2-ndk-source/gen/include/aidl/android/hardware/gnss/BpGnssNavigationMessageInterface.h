#pragma once

#include "aidl/android/hardware/gnss/IGnssNavigationMessageInterface.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssNavigationMessageInterface : public ::ndk::BpCInterface<IGnssNavigationMessageInterface> {
public:
  explicit BpGnssNavigationMessageInterface(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssNavigationMessageInterface();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssNavigationMessageCallback>& in_callback) override;
  ::ndk::ScopedAStatus close() override;
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
