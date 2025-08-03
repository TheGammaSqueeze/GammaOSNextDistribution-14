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
namespace authsecret {
class IAuthSecret : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IAuthSecret();
  virtual ~IAuthSecret();

  static const int32_t version = 1;
  static inline const std::string hash = "729cca96cb4732246b6ed1b3d15e2cbe63413afd";
  static constexpr uint32_t TRANSACTION_setPrimaryUserCredential = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IAuthSecret> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IAuthSecret>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IAuthSecret>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IAuthSecret>& impl);
  static const std::shared_ptr<IAuthSecret>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setPrimaryUserCredential(const std::vector<uint8_t>& in_secret) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IAuthSecret> default_impl;
};
class IAuthSecretDefault : public IAuthSecret {
public:
  ::ndk::ScopedAStatus setPrimaryUserCredential(const std::vector<uint8_t>& in_secret) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace authsecret
}  // namespace hardware
}  // namespace android
}  // namespace aidl
