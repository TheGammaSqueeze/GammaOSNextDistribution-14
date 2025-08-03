#pragma once

#include "aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsInterface.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
class BpMeasurementCorrectionsInterface : public ::ndk::BpCInterface<IMeasurementCorrectionsInterface> {
public:
  explicit BpMeasurementCorrectionsInterface(const ::ndk::SpAIBinder& binder);
  virtual ~BpMeasurementCorrectionsInterface();

  ::ndk::ScopedAStatus setCorrections(const ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections& in_corrections) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback>& in_callback) override;
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
