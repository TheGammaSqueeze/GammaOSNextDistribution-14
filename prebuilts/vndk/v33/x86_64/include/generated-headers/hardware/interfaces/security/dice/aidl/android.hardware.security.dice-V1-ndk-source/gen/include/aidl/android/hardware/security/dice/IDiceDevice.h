#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_ibinder_platform.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcel_platform.h>
#include <aidl/android/hardware/security/dice/Bcc.h>
#include <aidl/android/hardware/security/dice/BccHandover.h>
#include <aidl/android/hardware/security/dice/InputValues.h>
#include <aidl/android/hardware/security/dice/Signature.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
class IDiceDevice : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IDiceDevice();
  virtual ~IDiceDevice();

  static const int32_t version = 1;
  static inline const std::string hash = "02994f275fd7b1b40610c10eaeb0573f4312e358";
  static constexpr uint32_t TRANSACTION_sign = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getAttestationChain = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_derive = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_demote = FIRST_CALL_TRANSACTION + 3;

  static std::shared_ptr<IDiceDevice> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IDiceDevice>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IDiceDevice>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IDiceDevice>& impl);
  static const std::shared_ptr<IDiceDevice>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus sign(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_id, const std::vector<uint8_t>& in_payload, ::aidl::android::hardware::security::dice::Signature* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getAttestationChain(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::Bcc* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus derive(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::BccHandover* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus demote(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IDiceDevice> default_impl;
};
class IDiceDeviceDefault : public IDiceDevice {
public:
  ::ndk::ScopedAStatus sign(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_id, const std::vector<uint8_t>& in_payload, ::aidl::android::hardware::security::dice::Signature* _aidl_return) override;
  ::ndk::ScopedAStatus getAttestationChain(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::Bcc* _aidl_return) override;
  ::ndk::ScopedAStatus derive(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::BccHandover* _aidl_return) override;
  ::ndk::ScopedAStatus demote(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
