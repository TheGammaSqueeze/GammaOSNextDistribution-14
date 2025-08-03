#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioResponseInfo.h>
#include <aidl/android/hardware/radio/modem/ActivityStatsInfo.h>
#include <aidl/android/hardware/radio/modem/HardwareConfig.h>
#include <aidl/android/hardware/radio/modem/RadioCapability.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class IRadioModemResponse : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioModemResponse();
  virtual ~IRadioModemResponse();

  static const int32_t version = 1;
  static inline const std::string hash = "9dee2319b599d654955c05268c1eed6ca4373b58";
  static constexpr uint32_t TRANSACTION_acknowledgeRequest = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_enableModemResponse = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getBasebandVersionResponse = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getDeviceIdentityResponse = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getHardwareConfigResponse = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getModemActivityInfoResponse = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getModemStackStatusResponse = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getRadioCapabilityResponse = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_nvReadItemResponse = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_nvResetConfigResponse = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_nvWriteCdmaPrlResponse = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_nvWriteItemResponse = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_requestShutdownResponse = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_sendDeviceStateResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_setRadioCapabilityResponse = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_setRadioPowerResponse = FIRST_CALL_TRANSACTION + 15;

  static std::shared_ptr<IRadioModemResponse> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioModemResponse>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioModemResponse>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioModemResponse>& impl);
  static const std::shared_ptr<IRadioModemResponse>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus enableModemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getBasebandVersionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_version) = 0;
  virtual ::ndk::ScopedAStatus getDeviceIdentityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_imei, const std::string& in_imeisv, const std::string& in_esn, const std::string& in_meid) = 0;
  virtual ::ndk::ScopedAStatus getHardwareConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_config) = 0;
  virtual ::ndk::ScopedAStatus getModemActivityInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::ActivityStatsInfo& in_activityInfo) = 0;
  virtual ::ndk::ScopedAStatus getModemStackStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) = 0;
  virtual ::ndk::ScopedAStatus getRadioCapabilityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) = 0;
  virtual ::ndk::ScopedAStatus nvReadItemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_result) = 0;
  virtual ::ndk::ScopedAStatus nvResetConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus nvWriteCdmaPrlResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus nvWriteItemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus requestShutdownResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus sendDeviceStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setRadioCapabilityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) = 0;
  virtual ::ndk::ScopedAStatus setRadioPowerResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioModemResponse> default_impl;
};
class IRadioModemResponseDefault : public IRadioModemResponse {
public:
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override;
  ::ndk::ScopedAStatus enableModemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus getBasebandVersionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_version) override;
  ::ndk::ScopedAStatus getDeviceIdentityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_imei, const std::string& in_imeisv, const std::string& in_esn, const std::string& in_meid) override;
  ::ndk::ScopedAStatus getHardwareConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_config) override;
  ::ndk::ScopedAStatus getModemActivityInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::ActivityStatsInfo& in_activityInfo) override;
  ::ndk::ScopedAStatus getModemStackStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) override;
  ::ndk::ScopedAStatus getRadioCapabilityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override;
  ::ndk::ScopedAStatus nvReadItemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_result) override;
  ::ndk::ScopedAStatus nvResetConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus nvWriteCdmaPrlResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus nvWriteItemResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus requestShutdownResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus sendDeviceStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setRadioCapabilityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override;
  ::ndk::ScopedAStatus setRadioPowerResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
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
