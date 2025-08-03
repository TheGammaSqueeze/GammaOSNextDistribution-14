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
#include <aidl/android/hardware/gnss/GnssPowerStats.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssPowerIndicationCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssPowerIndicationCallback();
  virtual ~IGnssPowerIndicationCallback();

  enum : int32_t { CAPABILITY_TOTAL = 1 };
  enum : int32_t { CAPABILITY_SINGLEBAND_TRACKING = 2 };
  enum : int32_t { CAPABILITY_MULTIBAND_TRACKING = 4 };
  enum : int32_t { CAPABILITY_SINGLEBAND_ACQUISITION = 8 };
  enum : int32_t { CAPABILITY_MULTIBAND_ACQUISITION = 16 };
  enum : int32_t { CAPABILITY_OTHER_MODES = 32 };
  static const int32_t version = 1;
  static inline const std::string hash = "10839720b90aaec329521e810d9e0501cfcef0d3";
  static constexpr uint32_t TRANSACTION_setCapabilitiesCb = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_gnssPowerStatsCb = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IGnssPowerIndicationCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssPowerIndicationCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssPowerIndicationCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssPowerIndicationCallback>& impl);
  static const std::shared_ptr<IGnssPowerIndicationCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) = 0;
  virtual ::ndk::ScopedAStatus gnssPowerStatsCb(const ::aidl::android::hardware::gnss::GnssPowerStats& in_gnssPowerStats) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssPowerIndicationCallback> default_impl;
};
class IGnssPowerIndicationCallbackDefault : public IGnssPowerIndicationCallback {
public:
  ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) override;
  ::ndk::ScopedAStatus gnssPowerStatsCb(const ::aidl::android::hardware::gnss::GnssPowerStats& in_gnssPowerStats) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
