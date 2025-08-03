#pragma once

#include "aidl/android/hardware/gnss/IGnssMeasurementInterface.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssMeasurementInterface : public ::ndk::BpCInterface<IGnssMeasurementInterface> {
public:
  explicit BpGnssMeasurementInterface(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssMeasurementInterface();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, bool in_enableFullTracking, bool in_enableCorrVecOutputs) override;
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
