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
namespace soundtrigger3 {
class ISoundTriggerHwGlobalCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISoundTriggerHwGlobalCallback();
  virtual ~ISoundTriggerHwGlobalCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "7d8d63478cd50e766d2072140c8aa3457f9fb585";
  static constexpr uint32_t TRANSACTION_onResourcesAvailable = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<ISoundTriggerHwGlobalCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISoundTriggerHwGlobalCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISoundTriggerHwGlobalCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISoundTriggerHwGlobalCallback>& impl);
  static const std::shared_ptr<ISoundTriggerHwGlobalCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onResourcesAvailable() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISoundTriggerHwGlobalCallback> default_impl;
};
class ISoundTriggerHwGlobalCallbackDefault : public ISoundTriggerHwGlobalCallback {
public:
  ::ndk::ScopedAStatus onResourcesAvailable() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
