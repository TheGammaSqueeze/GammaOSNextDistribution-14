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
#include <aidl/android/hardware/oemlock/OemLockSecureStatus.h>

namespace aidl {
namespace android {
namespace hardware {
namespace oemlock {
class IOemLock : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IOemLock();
  virtual ~IOemLock();

  static const int32_t version = 1;
  static inline const std::string hash = "782d36d56fbdca1105672dd96b8e955b6a81dadf";
  static constexpr uint32_t TRANSACTION_getName = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_isOemUnlockAllowedByCarrier = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_isOemUnlockAllowedByDevice = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_setOemUnlockAllowedByCarrier = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_setOemUnlockAllowedByDevice = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IOemLock> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IOemLock>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IOemLock>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IOemLock>& impl);
  static const std::shared_ptr<IOemLock>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getName(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isOemUnlockAllowedByCarrier(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isOemUnlockAllowedByDevice(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setOemUnlockAllowedByCarrier(bool in_allowed, const std::vector<uint8_t>& in_signature, ::aidl::android::hardware::oemlock::OemLockSecureStatus* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setOemUnlockAllowedByDevice(bool in_allowed) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IOemLock> default_impl;
};
class IOemLockDefault : public IOemLock {
public:
  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus isOemUnlockAllowedByCarrier(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isOemUnlockAllowedByDevice(bool* _aidl_return) override;
  ::ndk::ScopedAStatus setOemUnlockAllowedByCarrier(bool in_allowed, const std::vector<uint8_t>& in_signature, ::aidl::android::hardware::oemlock::OemLockSecureStatus* _aidl_return) override;
  ::ndk::ScopedAStatus setOemUnlockAllowedByDevice(bool in_allowed) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace oemlock
}  // namespace hardware
}  // namespace android
}  // namespace aidl
