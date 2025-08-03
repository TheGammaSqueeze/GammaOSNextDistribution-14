#pragma once

#include "aidl/android/hardware/radio/messaging/IRadioMessaging.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class BpRadioMessaging : public ::ndk::BpCInterface<IRadioMessaging> {
public:
  explicit BpRadioMessaging(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioMessaging();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
