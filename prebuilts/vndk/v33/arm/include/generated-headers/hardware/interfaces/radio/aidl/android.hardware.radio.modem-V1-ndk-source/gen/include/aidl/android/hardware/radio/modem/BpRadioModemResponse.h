#pragma once

#include "aidl/android/hardware/radio/modem/IRadioModemResponse.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class BpRadioModemResponse : public ::ndk::BpCInterface<IRadioModemResponse> {
public:
  explicit BpRadioModemResponse(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioModemResponse();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
