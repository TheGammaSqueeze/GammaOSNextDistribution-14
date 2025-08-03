#pragma once

#include "aidl/android/hardware/gnss/IAGnssRil.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpAGnssRil : public ::ndk::BpCInterface<IAGnssRil> {
public:
  explicit BpAGnssRil(const ::ndk::SpAIBinder& binder);
  virtual ~BpAGnssRil();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRilCallback>& in_callback) override;
  ::ndk::ScopedAStatus setRefLocation(const ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocation& in_agnssReflocation) override;
  ::ndk::ScopedAStatus setSetId(::aidl::android::hardware::gnss::IAGnssRil::SetIdType in_type, const std::string& in_setid) override;
  ::ndk::ScopedAStatus updateNetworkState(const ::aidl::android::hardware::gnss::IAGnssRil::NetworkAttributes& in_attributes) override;
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
