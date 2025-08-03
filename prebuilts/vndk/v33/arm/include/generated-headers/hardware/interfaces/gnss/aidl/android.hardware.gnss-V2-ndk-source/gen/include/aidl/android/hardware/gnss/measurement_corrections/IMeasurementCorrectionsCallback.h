#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
class IMeasurementCorrectionsCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IMeasurementCorrectionsCallback();
  virtual ~IMeasurementCorrectionsCallback();

  enum : int32_t { CAPABILITY_LOS_SATS = 1 };
  enum : int32_t { CAPABILITY_EXCESS_PATH_LENGTH = 2 };
  enum : int32_t { CAPABILITY_REFLECTING_PLANE = 4 };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCapabilitiesCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IMeasurementCorrectionsCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IMeasurementCorrectionsCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IMeasurementCorrectionsCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IMeasurementCorrectionsCallback>& impl);
  static const std::shared_ptr<IMeasurementCorrectionsCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IMeasurementCorrectionsCallback> default_impl;
};
class IMeasurementCorrectionsCallbackDefault : public IMeasurementCorrectionsCallback {
public:
  ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
