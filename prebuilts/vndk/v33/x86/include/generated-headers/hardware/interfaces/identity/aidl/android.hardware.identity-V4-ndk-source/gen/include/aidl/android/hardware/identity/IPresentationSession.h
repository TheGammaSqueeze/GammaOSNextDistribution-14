#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/identity/IIdentityCredential.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class IPresentationSession : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IPresentationSession();
  virtual ~IPresentationSession();

  static const int32_t version = 4;
  static inline const std::string hash = "2d63b17eb0f860c87b3564eda281342fb362e38d";
  static constexpr uint32_t TRANSACTION_getEphemeralKeyPair = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getAuthChallenge = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_setReaderEphemeralPublicKey = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_setSessionTranscript = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getCredential = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IPresentationSession> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IPresentationSession>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IPresentationSession>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IPresentationSession>& impl);
  static const std::shared_ptr<IPresentationSession>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getEphemeralKeyPair(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getAuthChallenge(int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<uint8_t>& in_publicKey) = 0;
  virtual ::ndk::ScopedAStatus setSessionTranscript(const std::vector<uint8_t>& in_sessionTranscript) = 0;
  virtual ::ndk::ScopedAStatus getCredential(const std::vector<uint8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IPresentationSession> default_impl;
};
class IPresentationSessionDefault : public IPresentationSession {
public:
  ::ndk::ScopedAStatus getEphemeralKeyPair(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getAuthChallenge(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<uint8_t>& in_publicKey) override;
  ::ndk::ScopedAStatus setSessionTranscript(const std::vector<uint8_t>& in_sessionTranscript) override;
  ::ndk::ScopedAStatus getCredential(const std::vector<uint8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
