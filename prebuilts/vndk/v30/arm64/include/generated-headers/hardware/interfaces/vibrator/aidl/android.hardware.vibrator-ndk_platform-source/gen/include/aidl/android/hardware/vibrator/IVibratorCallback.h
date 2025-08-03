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
namespace vibrator {
class IVibratorCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IVibratorCallback();
  virtual ~IVibratorCallback();


  static const int32_t version = 1;
  static inline const std::string hash = "eeab78b6096b029f424ab5ce9c2c4ef1249a5cb0";

  static std::shared_ptr<IVibratorCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IVibratorCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IVibratorCallback>* instance);
  static bool setDefaultImpl(std::shared_ptr<IVibratorCallback> impl);
  static const std::shared_ptr<IVibratorCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onComplete() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IVibratorCallback> default_impl;
};
class IVibratorCallbackDefault : public IVibratorCallback {
public:
  ::ndk::ScopedAStatus onComplete() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
