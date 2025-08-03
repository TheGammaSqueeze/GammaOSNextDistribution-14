#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/uwb/UwbEvent.h>
#include <aidl/android/hardware/uwb/UwbStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
class IUwbClientCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IUwbClientCallback();
  virtual ~IUwbClientCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "5bffd1b593a254bcbe9cb0b2fcf29f71452ae0af";
  static constexpr uint32_t TRANSACTION_onUciMessage = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onHalEvent = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IUwbClientCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IUwbClientCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IUwbClientCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IUwbClientCallback>& impl);
  static const std::shared_ptr<IUwbClientCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onUciMessage(const std::vector<uint8_t>& in_data) = 0;
  virtual ::ndk::ScopedAStatus onHalEvent(::aidl::android::hardware::uwb::UwbEvent in_event, ::aidl::android::hardware::uwb::UwbStatus in_status) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IUwbClientCallback> default_impl;
};
class IUwbClientCallbackDefault : public IUwbClientCallback {
public:
  ::ndk::ScopedAStatus onUciMessage(const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus onHalEvent(::aidl::android::hardware::uwb::UwbEvent in_event, ::aidl::android::hardware::uwb::UwbStatus in_status) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
