#pragma once

#include "aidl/android/hardware/gnss/IGnssMeasurementCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssMeasurementCallback : public ::ndk::BpCInterface<IGnssMeasurementCallback> {
public:
  explicit BpGnssMeasurementCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssMeasurementCallback();

  ::ndk::ScopedAStatus gnssMeasurementCb(const ::aidl::android::hardware::gnss::GnssData& in_data) override;
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
