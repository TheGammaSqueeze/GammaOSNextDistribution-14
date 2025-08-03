#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/messaging/CdmaBroadcastSmsConfigInfo.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsAck.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsMessage.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsWriteArgs.h>
#include <aidl/android/hardware/radio/messaging/GsmBroadcastSmsConfigInfo.h>
#include <aidl/android/hardware/radio/messaging/GsmSmsMessage.h>
#include <aidl/android/hardware/radio/messaging/IRadioMessagingIndication.h>
#include <aidl/android/hardware/radio/messaging/IRadioMessagingResponse.h>
#include <aidl/android/hardware/radio/messaging/ImsSmsMessage.h>
#include <aidl/android/hardware/radio/messaging/SmsAcknowledgeFailCause.h>
#include <aidl/android/hardware/radio/messaging/SmsWriteArgs.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class IRadioMessaging : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioMessaging();
  virtual ~IRadioMessaging();

  static const int32_t version = 1;
  static inline const std::string hash = "5237ec5f500627b6b844b155e356e603157f9ba6";
  static constexpr uint32_t TRANSACTION_acknowledgeIncomingGsmSmsWithPdu = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_acknowledgeLastIncomingCdmaSms = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_acknowledgeLastIncomingGsmSms = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_deleteSmsOnRuim = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_deleteSmsOnSim = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getCdmaBroadcastConfig = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getGsmBroadcastConfig = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getSmscAddress = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_reportSmsMemoryStatus = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_responseAcknowledgement = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_sendCdmaSms = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_sendCdmaSmsExpectMore = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_sendImsSms = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_sendSms = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_sendSmsExpectMore = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_setCdmaBroadcastActivation = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_setCdmaBroadcastConfig = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_setGsmBroadcastActivation = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_setGsmBroadcastConfig = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_setResponseFunctions = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_setSmscAddress = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_writeSmsToRuim = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_writeSmsToSim = FIRST_CALL_TRANSACTION + 22;

  static std::shared_ptr<IRadioMessaging> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioMessaging>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioMessaging>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioMessaging>& impl);
  static const std::shared_ptr<IRadioMessaging>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acknowledgeIncomingGsmSmsWithPdu(int32_t in_serial, bool in_success, const std::string& in_ackPdu) = 0;
  virtual ::ndk::ScopedAStatus acknowledgeLastIncomingCdmaSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsAck& in_smsAck) = 0;
  virtual ::ndk::ScopedAStatus acknowledgeLastIncomingGsmSms(int32_t in_serial, bool in_success, ::aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause in_cause) = 0;
  virtual ::ndk::ScopedAStatus deleteSmsOnRuim(int32_t in_serial, int32_t in_index) = 0;
  virtual ::ndk::ScopedAStatus deleteSmsOnSim(int32_t in_serial, int32_t in_index) = 0;
  virtual ::ndk::ScopedAStatus getCdmaBroadcastConfig(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getGsmBroadcastConfig(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getSmscAddress(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus reportSmsMemoryStatus(int32_t in_serial, bool in_available) = 0;
  virtual ::ndk::ScopedAStatus responseAcknowledgement() = 0;
  virtual ::ndk::ScopedAStatus sendCdmaSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_sms) = 0;
  virtual ::ndk::ScopedAStatus sendCdmaSmsExpectMore(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_sms) = 0;
  virtual ::ndk::ScopedAStatus sendImsSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::ImsSmsMessage& in_message) = 0;
  virtual ::ndk::ScopedAStatus sendSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::GsmSmsMessage& in_message) = 0;
  virtual ::ndk::ScopedAStatus sendSmsExpectMore(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::GsmSmsMessage& in_message) = 0;
  virtual ::ndk::ScopedAStatus setCdmaBroadcastActivation(int32_t in_serial, bool in_activate) = 0;
  virtual ::ndk::ScopedAStatus setCdmaBroadcastConfig(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::messaging::CdmaBroadcastSmsConfigInfo>& in_configInfo) = 0;
  virtual ::ndk::ScopedAStatus setGsmBroadcastActivation(int32_t in_serial, bool in_activate) = 0;
  virtual ::ndk::ScopedAStatus setGsmBroadcastConfig(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::messaging::GsmBroadcastSmsConfigInfo>& in_configInfo) = 0;
  virtual ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::messaging::IRadioMessagingResponse>& in_radioMessagingResponse, const std::shared_ptr<::aidl::android::hardware::radio::messaging::IRadioMessagingIndication>& in_radioMessagingIndication) = 0;
  virtual ::ndk::ScopedAStatus setSmscAddress(int32_t in_serial, const std::string& in_smsc) = 0;
  virtual ::ndk::ScopedAStatus writeSmsToRuim(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsWriteArgs& in_cdmaSms) = 0;
  virtual ::ndk::ScopedAStatus writeSmsToSim(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::SmsWriteArgs& in_smsWriteArgs) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioMessaging> default_impl;
};
class IRadioMessagingDefault : public IRadioMessaging {
public:
  ::ndk::ScopedAStatus acknowledgeIncomingGsmSmsWithPdu(int32_t in_serial, bool in_success, const std::string& in_ackPdu) override;
  ::ndk::ScopedAStatus acknowledgeLastIncomingCdmaSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsAck& in_smsAck) override;
  ::ndk::ScopedAStatus acknowledgeLastIncomingGsmSms(int32_t in_serial, bool in_success, ::aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause in_cause) override;
  ::ndk::ScopedAStatus deleteSmsOnRuim(int32_t in_serial, int32_t in_index) override;
  ::ndk::ScopedAStatus deleteSmsOnSim(int32_t in_serial, int32_t in_index) override;
  ::ndk::ScopedAStatus getCdmaBroadcastConfig(int32_t in_serial) override;
  ::ndk::ScopedAStatus getGsmBroadcastConfig(int32_t in_serial) override;
  ::ndk::ScopedAStatus getSmscAddress(int32_t in_serial) override;
  ::ndk::ScopedAStatus reportSmsMemoryStatus(int32_t in_serial, bool in_available) override;
  ::ndk::ScopedAStatus responseAcknowledgement() override;
  ::ndk::ScopedAStatus sendCdmaSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_sms) override;
  ::ndk::ScopedAStatus sendCdmaSmsExpectMore(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_sms) override;
  ::ndk::ScopedAStatus sendImsSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::ImsSmsMessage& in_message) override;
  ::ndk::ScopedAStatus sendSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::GsmSmsMessage& in_message) override;
  ::ndk::ScopedAStatus sendSmsExpectMore(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::GsmSmsMessage& in_message) override;
  ::ndk::ScopedAStatus setCdmaBroadcastActivation(int32_t in_serial, bool in_activate) override;
  ::ndk::ScopedAStatus setCdmaBroadcastConfig(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::messaging::CdmaBroadcastSmsConfigInfo>& in_configInfo) override;
  ::ndk::ScopedAStatus setGsmBroadcastActivation(int32_t in_serial, bool in_activate) override;
  ::ndk::ScopedAStatus setGsmBroadcastConfig(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::messaging::GsmBroadcastSmsConfigInfo>& in_configInfo) override;
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::messaging::IRadioMessagingResponse>& in_radioMessagingResponse, const std::shared_ptr<::aidl::android::hardware::radio::messaging::IRadioMessagingIndication>& in_radioMessagingIndication) override;
  ::ndk::ScopedAStatus setSmscAddress(int32_t in_serial, const std::string& in_smsc) override;
  ::ndk::ScopedAStatus writeSmsToRuim(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsWriteArgs& in_cdmaSms) override;
  ::ndk::ScopedAStatus writeSmsToSim(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::SmsWriteArgs& in_smsWriteArgs) override;
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
