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
#include <aidl/android/hardware/security/sharedsecret/SharedSecretParameters.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace sharedsecret {
class ISharedSecret : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISharedSecret();
  virtual ~ISharedSecret();

  static const char* KEY_AGREEMENT_LABEL;
  static const char* KEY_CHECK_LABEL;
  static const int32_t version = 1;
  static inline const std::string hash = "aea3745b559d2977f0676952e510fb0547a52140";
  static constexpr uint32_t TRANSACTION_getSharedSecretParameters = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_computeSharedSecret = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<ISharedSecret> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISharedSecret>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISharedSecret>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISharedSecret>& impl);
  static const std::shared_ptr<ISharedSecret>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getSharedSecretParameters(::aidl::android::hardware::security::sharedsecret::SharedSecretParameters* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus computeSharedSecret(const std::vector<::aidl::android::hardware::security::sharedsecret::SharedSecretParameters>& in_params, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISharedSecret> default_impl;
};
class ISharedSecretDefault : public ISharedSecret {
public:
  ::ndk::ScopedAStatus getSharedSecretParameters(::aidl::android::hardware::security::sharedsecret::SharedSecretParameters* _aidl_return) override;
  ::ndk::ScopedAStatus computeSharedSecret(const std::vector<::aidl::android::hardware::security::sharedsecret::SharedSecretParameters>& in_params, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace sharedsecret
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
