#pragma once

#include "aidl/android/hardware/radio/messaging/IRadioMessaging.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class BnRadioMessaging : public ::ndk::BnCInterface<IRadioMessaging> {
public:
  BnRadioMessaging();
  virtual ~BnRadioMessaging();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioMessagingDelegator : public BnRadioMessaging {
public:
  explicit IRadioMessagingDelegator(const std::shared_ptr<IRadioMessaging> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioMessaging::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acknowledgeIncomingGsmSmsWithPdu(int32_t in_serial, bool in_success, const std::string& in_ackPdu) override {
    return _impl->acknowledgeIncomingGsmSmsWithPdu(in_serial, in_success, in_ackPdu);
  }
  ::ndk::ScopedAStatus acknowledgeLastIncomingCdmaSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsAck& in_smsAck) override {
    return _impl->acknowledgeLastIncomingCdmaSms(in_serial, in_smsAck);
  }
  ::ndk::ScopedAStatus acknowledgeLastIncomingGsmSms(int32_t in_serial, bool in_success, ::aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause in_cause) override {
    return _impl->acknowledgeLastIncomingGsmSms(in_serial, in_success, in_cause);
  }
  ::ndk::ScopedAStatus deleteSmsOnRuim(int32_t in_serial, int32_t in_index) override {
    return _impl->deleteSmsOnRuim(in_serial, in_index);
  }
  ::ndk::ScopedAStatus deleteSmsOnSim(int32_t in_serial, int32_t in_index) override {
    return _impl->deleteSmsOnSim(in_serial, in_index);
  }
  ::ndk::ScopedAStatus getCdmaBroadcastConfig(int32_t in_serial) override {
    return _impl->getCdmaBroadcastConfig(in_serial);
  }
  ::ndk::ScopedAStatus getGsmBroadcastConfig(int32_t in_serial) override {
    return _impl->getGsmBroadcastConfig(in_serial);
  }
  ::ndk::ScopedAStatus getSmscAddress(int32_t in_serial) override {
    return _impl->getSmscAddress(in_serial);
  }
  ::ndk::ScopedAStatus reportSmsMemoryStatus(int32_t in_serial, bool in_available) override {
    return _impl->reportSmsMemoryStatus(in_serial, in_available);
  }
  ::ndk::ScopedAStatus responseAcknowledgement() override {
    return _impl->responseAcknowledgement();
  }
  ::ndk::ScopedAStatus sendCdmaSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_sms) override {
    return _impl->sendCdmaSms(in_serial, in_sms);
  }
  ::ndk::ScopedAStatus sendCdmaSmsExpectMore(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_sms) override {
    return _impl->sendCdmaSmsExpectMore(in_serial, in_sms);
  }
  ::ndk::ScopedAStatus sendImsSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::ImsSmsMessage& in_message) override {
    return _impl->sendImsSms(in_serial, in_message);
  }
  ::ndk::ScopedAStatus sendSms(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::GsmSmsMessage& in_message) override {
    return _impl->sendSms(in_serial, in_message);
  }
  ::ndk::ScopedAStatus sendSmsExpectMore(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::GsmSmsMessage& in_message) override {
    return _impl->sendSmsExpectMore(in_serial, in_message);
  }
  ::ndk::ScopedAStatus setCdmaBroadcastActivation(int32_t in_serial, bool in_activate) override {
    return _impl->setCdmaBroadcastActivation(in_serial, in_activate);
  }
  ::ndk::ScopedAStatus setCdmaBroadcastConfig(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::messaging::CdmaBroadcastSmsConfigInfo>& in_configInfo) override {
    return _impl->setCdmaBroadcastConfig(in_serial, in_configInfo);
  }
  ::ndk::ScopedAStatus setGsmBroadcastActivation(int32_t in_serial, bool in_activate) override {
    return _impl->setGsmBroadcastActivation(in_serial, in_activate);
  }
  ::ndk::ScopedAStatus setGsmBroadcastConfig(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::messaging::GsmBroadcastSmsConfigInfo>& in_configInfo) override {
    return _impl->setGsmBroadcastConfig(in_serial, in_configInfo);
  }
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::messaging::IRadioMessagingResponse>& in_radioMessagingResponse, const std::shared_ptr<::aidl::android::hardware::radio::messaging::IRadioMessagingIndication>& in_radioMessagingIndication) override {
    return _impl->setResponseFunctions(in_radioMessagingResponse, in_radioMessagingIndication);
  }
  ::ndk::ScopedAStatus setSmscAddress(int32_t in_serial, const std::string& in_smsc) override {
    return _impl->setSmscAddress(in_serial, in_smsc);
  }
  ::ndk::ScopedAStatus writeSmsToRuim(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::CdmaSmsWriteArgs& in_cdmaSms) override {
    return _impl->writeSmsToRuim(in_serial, in_cdmaSms);
  }
  ::ndk::ScopedAStatus writeSmsToSim(int32_t in_serial, const ::aidl::android::hardware::radio::messaging::SmsWriteArgs& in_smsWriteArgs) override {
    return _impl->writeSmsToSim(in_serial, in_smsWriteArgs);
  }
protected:
private:
  std::shared_ptr<IRadioMessaging> _impl;
};

}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
