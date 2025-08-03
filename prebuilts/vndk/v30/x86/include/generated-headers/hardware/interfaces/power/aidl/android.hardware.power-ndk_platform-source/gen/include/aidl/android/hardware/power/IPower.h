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
#include <aidl/android/hardware/power/Boost.h>
#include <aidl/android/hardware/power/Mode.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
class IPower : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IPower();
  virtual ~IPower();


  static const int32_t version = 1;
  static inline const std::string hash = "d5bbe80a8c4df49931e8453f3138820e82dc525c";

  static std::shared_ptr<IPower> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IPower>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IPower>* instance);
  static bool setDefaultImpl(std::shared_ptr<IPower> impl);
  static const std::shared_ptr<IPower>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setMode(::aidl::android::hardware::power::Mode in_type, bool in_enabled) = 0;
  virtual ::ndk::ScopedAStatus isModeSupported(::aidl::android::hardware::power::Mode in_type, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setBoost(::aidl::android::hardware::power::Boost in_type, int32_t in_durationMs) = 0;
  virtual ::ndk::ScopedAStatus isBoostSupported(::aidl::android::hardware::power::Boost in_type, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IPower> default_impl;
};
class IPowerDefault : public IPower {
public:
  ::ndk::ScopedAStatus setMode(::aidl::android::hardware::power::Mode in_type, bool in_enabled) override;
  ::ndk::ScopedAStatus isModeSupported(::aidl::android::hardware::power::Mode in_type, bool* _aidl_return) override;
  ::ndk::ScopedAStatus setBoost(::aidl::android::hardware::power::Boost in_type, int32_t in_durationMs) override;
  ::ndk::ScopedAStatus isBoostSupported(::aidl::android::hardware::power::Boost in_type, bool* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
