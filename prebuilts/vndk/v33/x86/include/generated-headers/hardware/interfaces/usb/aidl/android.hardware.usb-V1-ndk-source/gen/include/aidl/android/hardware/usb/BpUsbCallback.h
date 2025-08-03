#pragma once

#include "aidl/android/hardware/usb/IUsbCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace usb {
class BpUsbCallback : public ::ndk::BpCInterface<IUsbCallback> {
public:
  explicit BpUsbCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpUsbCallback();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
