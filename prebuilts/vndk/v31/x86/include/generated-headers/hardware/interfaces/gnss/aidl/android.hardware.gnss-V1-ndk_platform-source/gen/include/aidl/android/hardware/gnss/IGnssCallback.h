#pragma once

#include <android/binder_interface_utils.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssCallback();
  virtual ~IGnssCallback();

  enum : int32_t { CAPABILITY_SATELLITE_BLOCKLIST = 512 };
  enum : int32_t { CAPABILITY_CORRELATION_VECTOR = 4096 };
  enum : int32_t { CAPABILITY_SATELLITE_PVT = 8192 };
  enum : int32_t { CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING = 16384 };
  static const int32_t version = 1;
  static inline const std::string hash = "10839720b90aaec329521e810d9e0501cfcef0d3";
  static constexpr uint32_t TRANSACTION_gnssSetCapabilitiesCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssCallback>& impl);
  static const std::shared_ptr<IGnssCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssSetCapabilitiesCb(int32_t in_capabilities) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssCallback> default_impl;
};
class IGnssCallbackDefault : public IGnssCallback {
public:
  ::ndk::ScopedAStatus gnssSetCapabilitiesCb(int32_t in_capabilities) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
