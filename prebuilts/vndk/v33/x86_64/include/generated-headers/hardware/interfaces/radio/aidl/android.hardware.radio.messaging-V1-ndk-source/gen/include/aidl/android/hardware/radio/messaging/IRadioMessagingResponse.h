#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioResponseInfo.h>
#include <aidl/android/hardware/radio/messaging/CdmaBroadcastSmsConfigInfo.h>
#include <aidl/android/hardware/radio/messaging/GsmBroadcastSmsConfigInfo.h>
#include <aidl/android/hardware/radio/messaging/SendSmsResult.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class IRadioMessagingResponse : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioMessagingResponse();
  virtual ~IRadioMessagingResponse();

  static const int32_t version = 1;
  static inline const std::string hash = "5237ec5f500627b6b844b155e356e603157f9ba6";
  static constexpr uint32_t TRANSACTION_acknowledgeIncomingGsmSmsWithPduResponse = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_acknowledgeLastIncomingCdmaSmsResponse = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_acknowledgeLastIncomingGsmSmsResponse = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_acknowledgeRequest = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_deleteSmsOnRuimResponse = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_deleteSmsOnSimResponse = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getCdmaBroadcastConfigResponse = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getGsmBroadcastConfigResponse = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getSmscAddressResponse = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_reportSmsMemoryStatusResponse = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_sendCdmaSmsExpectMoreResponse = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_sendCdmaSmsResponse = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_sendImsSmsResponse = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_sendSmsExpectMoreResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_sendSmsResponse = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_setCdmaBroadcastActivationResponse = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_setCdmaBroadcastConfigResponse = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_setGsmBroadcastActivationResponse = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_setGsmBroadcastConfigResponse = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_setSmscAddressResponse = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_writeSmsToRuimResponse = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_writeSmsToSimResponse = FIRST_CALL_TRANSACTION + 21;

  static std::shared_ptr<IRadioMessagingResponse> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioMessagingResponse>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioMessagingResponse>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioMessagingResponse>& impl);
  static const std::shared_ptr<IRadioMessagingResponse>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acknowledgeIncomingGsmSmsWithPduResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus acknowledgeLastIncomingCdmaSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus acknowledgeLastIncomingGsmSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus deleteSmsOnRuimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus deleteSmsOnSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getCdmaBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::messaging::CdmaBroadcastSmsConfigInfo>& in_configs) = 0;
  virtual ::ndk::ScopedAStatus getGsmBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::messaging::GsmBroadcastSmsConfigInfo>& in_configs) = 0;
  virtual ::ndk::ScopedAStatus getSmscAddressResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_smsc) = 0;
  virtual ::ndk::ScopedAStatus reportSmsMemoryStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus sendCdmaSmsExpectMoreResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) = 0;
  virtual ::ndk::ScopedAStatus sendCdmaSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) = 0;
  virtual ::ndk::ScopedAStatus sendImsSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) = 0;
  virtual ::ndk::ScopedAStatus sendSmsExpectMoreResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) = 0;
  virtual ::ndk::ScopedAStatus sendSmsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::messaging::SendSmsResult& in_sms) = 0;
  virtual ::ndk::ScopedAStatus setCdmaBroadcastActivationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCdmaBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setGsmBroadcastActivationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setGsmBroadcastConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setSmscAddressResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus writeSmsToRuimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_index) = 0;
  virtual ::ndk::ScopedAStatus writeSmsToSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_index) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioMessagingResponse> default_impl;
};
class IRadioMessagingResponseDefault : public IRadioMessagingResponse {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
