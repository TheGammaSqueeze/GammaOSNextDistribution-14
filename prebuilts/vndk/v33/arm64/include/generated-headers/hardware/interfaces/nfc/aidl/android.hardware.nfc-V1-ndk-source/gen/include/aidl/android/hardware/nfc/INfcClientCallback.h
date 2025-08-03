#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/nfc/NfcEvent.h>
#include <aidl/android/hardware/nfc/NfcStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
class INfcClientCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  INfcClientCallback();
  virtual ~INfcClientCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "37acf94ceb095ad537b66c3bd77c30a6ad5f5b0e";
  static constexpr uint32_t TRANSACTION_sendData = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_sendEvent = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<INfcClientCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INfcClientCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INfcClientCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<INfcClientCallback>& impl);
  static const std::shared_ptr<INfcClientCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus sendData(const std::vector<uint8_t>& in_data) = 0;
  virtual ::ndk::ScopedAStatus sendEvent(::aidl::android::hardware::nfc::NfcEvent in_event, ::aidl::android::hardware::nfc::NfcStatus in_status) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<INfcClientCallback> default_impl;
};
class INfcClientCallbackDefault : public INfcClientCallback {
public:
  ::ndk::ScopedAStatus sendData(const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus sendEvent(::aidl::android::hardware::nfc::NfcEvent in_event, ::aidl::android::hardware::nfc::NfcStatus in_status) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
