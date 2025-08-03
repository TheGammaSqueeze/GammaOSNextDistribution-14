#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/usb/IUsbCallback.h>
#include <aidl/android/hardware/usb/PortRole.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace usb {
class IUsb : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IUsb();
  virtual ~IUsb();

  static const int32_t version = 1;
  static inline const std::string hash = "9762531142d72e03bb4228209846c135f276d40e";
  static constexpr uint32_t TRANSACTION_enableContaminantPresenceDetection = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_enableUsbData = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_enableUsbDataWhileDocked = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_queryPortStatus = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_switchRole = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_limitPowerTransfer = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_resetUsbPort = FIRST_CALL_TRANSACTION + 7;

  static std::shared_ptr<IUsb> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IUsb>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IUsb>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IUsb>& impl);
  static const std::shared_ptr<IUsb>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus enableContaminantPresenceDetection(const std::string& in_portName, bool in_enable, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus enableUsbData(const std::string& in_portName, bool in_enable, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus enableUsbDataWhileDocked(const std::string& in_portName, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus queryPortStatus(int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::usb::IUsbCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus switchRole(const std::string& in_portName, const ::aidl::android::hardware::usb::PortRole& in_role, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus limitPowerTransfer(const std::string& in_portName, bool in_limit, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus resetUsbPort(const std::string& in_portName, int64_t in_transactionId) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IUsb> default_impl;
};
class IUsbDefault : public IUsb {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
