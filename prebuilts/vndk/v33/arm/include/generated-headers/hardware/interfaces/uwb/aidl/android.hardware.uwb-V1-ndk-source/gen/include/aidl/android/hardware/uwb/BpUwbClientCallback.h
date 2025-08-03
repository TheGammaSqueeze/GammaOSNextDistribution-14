#pragma once

#include "aidl/android/hardware/uwb/IUwbClientCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
class BpUwbClientCallback : public ::ndk::BpCInterface<IUwbClientCallback> {
public:
  explicit BpUwbClientCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpUwbClientCallback();

  ::ndk::ScopedAStatus onUciMessage(const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus onHalEvent(::aidl::android::hardware::uwb::UwbEvent in_event, ::aidl::android::hardware::uwb::UwbStatus in_status) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
