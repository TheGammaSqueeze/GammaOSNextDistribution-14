#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/health/HealthInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace health {
class IHealthInfoCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IHealthInfoCallback();
  virtual ~IHealthInfoCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "94e77215594f8ad98ab33a769263d48fdabed92e";
  static constexpr uint32_t TRANSACTION_healthInfoChanged = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IHealthInfoCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IHealthInfoCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IHealthInfoCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IHealthInfoCallback>& impl);
  static const std::shared_ptr<IHealthInfoCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus healthInfoChanged(const ::aidl::android::hardware::health::HealthInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IHealthInfoCallback> default_impl;
};
class IHealthInfoCallbackDefault : public IHealthInfoCallback {
public:
  ::ndk::ScopedAStatus healthInfoChanged(const ::aidl::android::hardware::health::HealthInfo& in_info) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
