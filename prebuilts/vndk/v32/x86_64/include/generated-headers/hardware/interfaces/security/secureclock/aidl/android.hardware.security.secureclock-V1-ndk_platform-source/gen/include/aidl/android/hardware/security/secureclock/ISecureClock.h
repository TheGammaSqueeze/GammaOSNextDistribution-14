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
#include <aidl/android/hardware/security/secureclock/TimeStampToken.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace secureclock {
class ISecureClock : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISecureClock();
  virtual ~ISecureClock();

  static const char* TIME_STAMP_MAC_LABEL;
  static const int32_t version = 1;
  static inline const std::string hash = "cd55ca9963c6a57fa5f2f120a45c6e0c4fafb423";
  static constexpr uint32_t TRANSACTION_generateTimeStamp = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<ISecureClock> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISecureClock>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISecureClock>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISecureClock>& impl);
  static const std::shared_ptr<ISecureClock>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus generateTimeStamp(int64_t in_challenge, ::aidl::android::hardware::security::secureclock::TimeStampToken* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISecureClock> default_impl;
};
class ISecureClockDefault : public ISecureClock {
public:
  ::ndk::ScopedAStatus generateTimeStamp(int64_t in_challenge, ::aidl::android::hardware::security::secureclock::TimeStampToken* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace secureclock
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
