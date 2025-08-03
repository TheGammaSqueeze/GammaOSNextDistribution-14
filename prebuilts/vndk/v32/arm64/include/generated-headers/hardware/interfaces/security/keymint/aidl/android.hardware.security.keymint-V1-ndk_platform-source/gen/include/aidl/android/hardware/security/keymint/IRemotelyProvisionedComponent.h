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
#include <aidl/android/hardware/security/keymint/DeviceInfo.h>
#include <aidl/android/hardware/security/keymint/MacedPublicKey.h>
#include <aidl/android/hardware/security/keymint/ProtectedData.h>
#include <aidl/android/hardware/security/keymint/RpcHardwareInfo.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class IRemotelyProvisionedComponent : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRemotelyProvisionedComponent();
  virtual ~IRemotelyProvisionedComponent();

  enum : int32_t { STATUS_FAILED = 1 };
  enum : int32_t { STATUS_INVALID_MAC = 2 };
  enum : int32_t { STATUS_PRODUCTION_KEY_IN_TEST_REQUEST = 3 };
  enum : int32_t { STATUS_TEST_KEY_IN_PRODUCTION_REQUEST = 4 };
  enum : int32_t { STATUS_INVALID_EEK = 5 };
  static const int32_t version = 1;
  static inline const std::string hash = "976674616001f714f4a4df49ee45f548de828524";
  static constexpr uint32_t TRANSACTION_getHardwareInfo = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_generateEcdsaP256KeyPair = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_generateCertificateRequest = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<IRemotelyProvisionedComponent> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRemotelyProvisionedComponent>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRemotelyProvisionedComponent>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRemotelyProvisionedComponent>& impl);
  static const std::shared_ptr<IRemotelyProvisionedComponent>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getHardwareInfo(::aidl::android::hardware::security::keymint::RpcHardwareInfo* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus generateEcdsaP256KeyPair(bool in_testMode, ::aidl::android::hardware::security::keymint::MacedPublicKey* out_macedPublicKey, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus generateCertificateRequest(bool in_testMode, const std::vector<::aidl::android::hardware::security::keymint::MacedPublicKey>& in_keysToSign, const std::vector<uint8_t>& in_endpointEncryptionCertChain, const std::vector<uint8_t>& in_challenge, ::aidl::android::hardware::security::keymint::DeviceInfo* out_deviceInfo, ::aidl::android::hardware::security::keymint::ProtectedData* out_protectedData, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRemotelyProvisionedComponent> default_impl;
};
class IRemotelyProvisionedComponentDefault : public IRemotelyProvisionedComponent {
public:
  ::ndk::ScopedAStatus getHardwareInfo(::aidl::android::hardware::security::keymint::RpcHardwareInfo* _aidl_return) override;
  ::ndk::ScopedAStatus generateEcdsaP256KeyPair(bool in_testMode, ::aidl::android::hardware::security::keymint::MacedPublicKey* out_macedPublicKey, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus generateCertificateRequest(bool in_testMode, const std::vector<::aidl::android::hardware::security::keymint::MacedPublicKey>& in_keysToSign, const std::vector<uint8_t>& in_endpointEncryptionCertChain, const std::vector<uint8_t>& in_challenge, ::aidl::android::hardware::security::keymint::DeviceInfo* out_deviceInfo, ::aidl::android::hardware::security::keymint::ProtectedData* out_protectedData, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
