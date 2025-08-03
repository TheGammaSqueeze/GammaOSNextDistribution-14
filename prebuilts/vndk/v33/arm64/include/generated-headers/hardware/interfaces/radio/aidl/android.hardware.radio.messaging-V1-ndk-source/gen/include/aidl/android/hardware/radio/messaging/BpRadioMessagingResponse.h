#pragma once

#include "aidl/android/hardware/radio/messaging/IRadioMessagingResponse.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class BpRadioMessagingResponse : public ::ndk::BpCInterface<IRadioMessagingResponse> {
public:
  explicit BpRadioMessagingResponse(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioMessagingResponse();

  ::ndk::ScopedAStatus acknowledgeIncomingGsmSmsWithPduResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus acknowledgeLastIncomingCdmaSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus acknowledgeLastIncomingGsmSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override;
  ::ndk::ScopedAStatus deleteSmsOnRuimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus deleteSmsOnSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus getCdmaBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::messaging::CdmaBroadcastSmsConfigInfo>& in_configs) override;
  ::ndk::ScopedAStatus getGsmBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::messaging::GsmBroadcastSmsConfigInfo>& in_configs) override;
  ::ndk::ScopedAStatus getSmscAddressResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_smsc) override;
  ::ndk::ScopedAStatus reportSmsMemoryStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus sendCdmaSmsExpectMoreResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override;
  ::ndk::ScopedAStatus sendCdmaSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override;
  ::ndk::ScopedAStatus sendImsSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override;
  ::ndk::ScopedAStatus sendSmsExpectMoreResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override;
  ::ndk::ScopedAStatus sendSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) override;
  ::ndk::ScopedAStatus setCdmaBroadcastActivationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCdmaBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setGsmBroadcastActivationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setGsmBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setSmscAddressResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus writeSmsToRuimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_index) override;
  ::ndk::ScopedAStatus writeSmsToSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_index) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
