#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/usb/PortRole.h>
#include <aidl/android/hardware/usb/PortStatus.h>
#include <aidl/android/hardware/usb/Status.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace usb {
class IUsbCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IUsbCallback();
  virtual ~IUsbCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "9762531142d72e03bb4228209846c135f276d40e";
  static constexpr uint32_t TRANSACTION_notifyPortStatusChange = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_notifyRoleSwitchStatus = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_notifyEnableUsbDataStatus = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_notifyEnableUsbDataWhileDockedStatus = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_notifyContaminantEnabledStatus = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_notifyQueryPortStatus = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_notifyLimitPowerTransferStatus = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_notifyResetUsbPortStatus = FIRST_CALL_TRANSACTION + 7;

  static std::shared_ptr<IUsbCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IUsbCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IUsbCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IUsbCallback>& impl);
  static const std::shared_ptr<IUsbCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus notifyPortStatusChange(const std::vector<::aidl::android::hardware::usb::PortStatus>& in_currentPortStatus, ::aidl::android::hardware::usb::Status in_retval) = 0;
  virtual ::ndk::ScopedAStatus notifyRoleSwitchStatus(const std::string& in_portName, const ::aidl::android::hardware::usb::PortRole& in_newRole, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus notifyEnableUsbDataStatus(const std::string& in_portName, bool in_enable, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus notifyEnableUsbDataWhileDockedStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus notifyContaminantEnabledStatus(const std::string& in_portName, bool in_enable, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus notifyQueryPortStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus notifyLimitPowerTransferStatus(const std::string& in_portName, bool in_limit, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus notifyResetUsbPortStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IUsbCallback> default_impl;
};
class IUsbCallbackDefault : public IUsbCallback {
public:
  ::ndk::ScopedAStatus notifyPortStatusChange(const std::vector<::aidl::android::hardware::usb::PortStatus>& in_currentPortStatus, ::aidl::android::hardware::usb::Status in_retval) override;
  ::ndk::ScopedAStatus notifyRoleSwitchStatus(const std::string& in_portName, const ::aidl::android::hardware::usb::PortRole& in_newRole, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus notifyEnableUsbDataStatus(const std::string& in_portName, bool in_enable, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus notifyEnableUsbDataWhileDockedStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus notifyContaminantEnabledStatus(const std::string& in_portName, bool in_enable, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus notifyQueryPortStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus notifyLimitPowerTransferStatus(const std::string& in_portName, bool in_limit, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus notifyResetUsbPortStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
