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
namespace wifi {
namespace supplicant {
class ISupplicantCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantCallback();
  virtual ~ISupplicantCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_onInterfaceCreated = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onInterfaceRemoved = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<ISupplicantCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantCallback>& impl);
  static const std::shared_ptr<ISupplicantCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onInterfaceCreated(const std::string& in_ifaceName) = 0;
  virtual ::ndk::ScopedAStatus onInterfaceRemoved(const std::string& in_ifaceName) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantCallback> default_impl;
};
class ISupplicantCallbackDefault : public ISupplicantCallback {
public:
  ::ndk::ScopedAStatus onInterfaceCreated(const std::string& in_ifaceName) override;
  ::ndk::ScopedAStatus onInterfaceRemoved(const std::string& in_ifaceName) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
