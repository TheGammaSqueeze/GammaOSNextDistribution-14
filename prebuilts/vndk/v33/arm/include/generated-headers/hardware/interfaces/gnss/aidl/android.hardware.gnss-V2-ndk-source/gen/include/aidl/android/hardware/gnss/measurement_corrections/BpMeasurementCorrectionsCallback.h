#pragma once

#include "aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
class BpMeasurementCorrectionsCallback : public ::ndk::BpCInterface<IMeasurementCorrectionsCallback> {
public:
  explicit BpMeasurementCorrectionsCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpMeasurementCorrectionsCallback();

  ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
