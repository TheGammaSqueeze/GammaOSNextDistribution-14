#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioIndicationType.h>
#include <aidl/android/hardware/radio/modem/HardwareConfig.h>
#include <aidl/android/hardware/radio/modem/RadioCapability.h>
#include <aidl/android/hardware/radio/modem/RadioState.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class IRadioModemIndication : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioModemIndication();
  virtual ~IRadioModemIndication();

  static const int32_t version = 1;
  static inline const std::string hash = "9dee2319b599d654955c05268c1eed6ca4373b58";
  static constexpr uint32_t TRANSACTION_hardwareConfigChanged = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_modemReset = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_radioCapabilityIndication = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_radioStateChanged = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_rilConnected = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IRadioModemIndication> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioModemIndication>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioModemIndication>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioModemIndication>& impl);
  static const std::shared_ptr<IRadioModemIndication>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus hardwareConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_configs) = 0;
  virtual ::ndk::ScopedAStatus modemReset(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_reason) = 0;
  virtual ::ndk::ScopedAStatus radioCapabilityIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) = 0;
  virtual ::ndk::ScopedAStatus radioStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::modem::RadioState in_radioState) = 0;
  virtual ::ndk::ScopedAStatus rilConnected(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioModemIndication> default_impl;
};
class IRadioModemIndicationDefault : public IRadioModemIndication {
public:
  ::ndk::ScopedAStatus hardwareConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_configs) override;
  ::ndk::ScopedAStatus modemReset(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_reason) override;
  ::ndk::ScopedAStatus radioCapabilityIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override;
  ::ndk::ScopedAStatus radioStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::modem::RadioState in_radioState) override;
  ::ndk::ScopedAStatus rilConnected(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
