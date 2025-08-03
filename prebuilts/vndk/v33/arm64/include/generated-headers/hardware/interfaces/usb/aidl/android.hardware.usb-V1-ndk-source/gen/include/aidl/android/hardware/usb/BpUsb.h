#pragma once

#include "aidl/android/hardware/usb/IUsb.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace usb {
class BpUsb : public ::ndk::BpCInterface<IUsb> {
public:
  explicit BpUsb(const ::ndk::SpAIBinder& binder);
  virtual ~BpUsb();

  ::ndk::ScopedAStatus enableContaminantPresenceDetection(const std::string& in_portName, bool in_enable, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus enableUsbData(const std::string& in_portName, bool in_enable, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus enableUsbDataWhileDocked(const std::string& in_portName, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus queryPortStatus(int64_t in_transactionId) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::usb::IUsbCallback>& in_callback) override;
  ::ndk::ScopedAStatus switchRole(const std::string& in_portName, const ::aidl::android::hardware::usb::PortRole& in_role, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus limitPowerTransfer(const std::string& in_portName, bool in_limit, int64_t in_transactionId) override;
  ::ndk::ScopedAStatus resetUsbPort(const std::string& in_portName, int64_t in_transactionId) override;
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
