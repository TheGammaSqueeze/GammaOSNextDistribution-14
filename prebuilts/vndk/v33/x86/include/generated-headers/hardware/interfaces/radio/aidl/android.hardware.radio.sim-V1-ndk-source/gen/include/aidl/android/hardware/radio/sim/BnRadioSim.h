#pragma once

#include "aidl/android/hardware/radio/sim/IRadioSim.h"

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
namespace sim {
class BnRadioSim : public ::ndk::BnCInterface<IRadioSim> {
public:
  BnRadioSim();
  virtual ~BnRadioSim();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioSimDelegator : public BnRadioSim {
public:
  explicit IRadioSimDelegator(const std::shared_ptr<IRadioSim> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioSim::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus areUiccApplicationsEnabled(int32_t in_serial) override {
    return _impl->areUiccApplicationsEnabled(in_serial);
  }
  ::ndk::ScopedAStatus changeIccPin2ForApp(int32_t in_serial, const std::string& in_oldPin2, const std::string& in_newPin2, const std::string& in_aid) override {
    return _impl->changeIccPin2ForApp(in_serial, in_oldPin2, in_newPin2, in_aid);
  }
  ::ndk::ScopedAStatus changeIccPinForApp(int32_t in_serial, const std::string& in_oldPin, const std::string& in_newPin, const std::string& in_aid) override {
    return _impl->changeIccPinForApp(in_serial, in_oldPin, in_newPin, in_aid);
  }
  ::ndk::ScopedAStatus enableUiccApplications(int32_t in_serial, bool in_enable) override {
    return _impl->enableUiccApplications(in_serial, in_enable);
  }
  ::ndk::ScopedAStatus getAllowedCarriers(int32_t in_serial) override {
    return _impl->getAllowedCarriers(in_serial);
  }
  ::ndk::ScopedAStatus getCdmaSubscription(int32_t in_serial) override {
    return _impl->getCdmaSubscription(in_serial);
  }
  ::ndk::ScopedAStatus getCdmaSubscriptionSource(int32_t in_serial) override {
    return _impl->getCdmaSubscriptionSource(in_serial);
  }
  ::ndk::ScopedAStatus getFacilityLockForApp(int32_t in_serial, const std::string& in_facility, const std::string& in_password, int32_t in_serviceClass, const std::string& in_appId) override {
    return _impl->getFacilityLockForApp(in_serial, in_facility, in_password, in_serviceClass, in_appId);
  }
  ::ndk::ScopedAStatus getIccCardStatus(int32_t in_serial) override {
    return _impl->getIccCardStatus(in_serial);
  }
  ::ndk::ScopedAStatus getImsiForApp(int32_t in_serial, const std::string& in_aid) override {
    return _impl->getImsiForApp(in_serial, in_aid);
  }
  ::ndk::ScopedAStatus getSimPhonebookCapacity(int32_t in_serial) override {
    return _impl->getSimPhonebookCapacity(in_serial);
  }
  ::ndk::ScopedAStatus getSimPhonebookRecords(int32_t in_serial) override {
    return _impl->getSimPhonebookRecords(in_serial);
  }
  ::ndk::ScopedAStatus iccCloseLogicalChannel(int32_t in_serial, int32_t in_channelId) override {
    return _impl->iccCloseLogicalChannel(in_serial, in_channelId);
  }
  ::ndk::ScopedAStatus iccIoForApp(int32_t in_serial, const ::aidl::android::hardware::radio::sim::IccIo& in_iccIo) override {
    return _impl->iccIoForApp(in_serial, in_iccIo);
  }
  ::ndk::ScopedAStatus iccOpenLogicalChannel(int32_t in_serial, const std::string& in_aid, int32_t in_p2) override {
    return _impl->iccOpenLogicalChannel(in_serial, in_aid, in_p2);
  }
  ::ndk::ScopedAStatus iccTransmitApduBasicChannel(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SimApdu& in_message) override {
    return _impl->iccTransmitApduBasicChannel(in_serial, in_message);
  }
  ::ndk::ScopedAStatus iccTransmitApduLogicalChannel(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SimApdu& in_message) override {
    return _impl->iccTransmitApduLogicalChannel(in_serial, in_message);
  }
  ::ndk::ScopedAStatus reportStkServiceIsRunning(int32_t in_serial) override {
    return _impl->reportStkServiceIsRunning(in_serial);
  }
  ::ndk::ScopedAStatus requestIccSimAuthentication(int32_t in_serial, int32_t in_authContext, const std::string& in_authData, const std::string& in_aid) override {
    return _impl->requestIccSimAuthentication(in_serial, in_authContext, in_authData, in_aid);
  }
  ::ndk::ScopedAStatus responseAcknowledgement() override {
    return _impl->responseAcknowledgement();
  }
  ::ndk::ScopedAStatus sendEnvelope(int32_t in_serial, const std::string& in_contents) override {
    return _impl->sendEnvelope(in_serial, in_contents);
  }
  ::ndk::ScopedAStatus sendEnvelopeWithStatus(int32_t in_serial, const std::string& in_contents) override {
    return _impl->sendEnvelopeWithStatus(in_serial, in_contents);
  }
  ::ndk::ScopedAStatus sendTerminalResponseToSim(int32_t in_serial, const std::string& in_contents) override {
    return _impl->sendTerminalResponseToSim(in_serial, in_contents);
  }
  ::ndk::ScopedAStatus setAllowedCarriers(int32_t in_serial, const ::aidl::android::hardware::radio::sim::CarrierRestrictions& in_carriers, ::aidl::android::hardware::radio::sim::SimLockMultiSimPolicy in_multiSimPolicy) override {
    return _impl->setAllowedCarriers(in_serial, in_carriers, in_multiSimPolicy);
  }
  ::ndk::ScopedAStatus setCarrierInfoForImsiEncryption(int32_t in_serial, const ::aidl::android::hardware::radio::sim::ImsiEncryptionInfo& in_imsiEncryptionInfo) override {
    return _impl->setCarrierInfoForImsiEncryption(in_serial, in_imsiEncryptionInfo);
  }
  ::ndk::ScopedAStatus setCdmaSubscriptionSource(int32_t in_serial, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_cdmaSub) override {
    return _impl->setCdmaSubscriptionSource(in_serial, in_cdmaSub);
  }
  ::ndk::ScopedAStatus setFacilityLockForApp(int32_t in_serial, const std::string& in_facility, bool in_lockState, const std::string& in_password, int32_t in_serviceClass, const std::string& in_appId) override {
    return _impl->setFacilityLockForApp(in_serial, in_facility, in_lockState, in_password, in_serviceClass, in_appId);
  }
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::sim::IRadioSimResponse>& in_radioSimResponse, const std::shared_ptr<::aidl::android::hardware::radio::sim::IRadioSimIndication>& in_radioSimIndication) override {
    return _impl->setResponseFunctions(in_radioSimResponse, in_radioSimIndication);
  }
  ::ndk::ScopedAStatus setSimCardPower(int32_t in_serial, ::aidl::android::hardware::radio::sim::CardPowerState in_powerUp) override {
    return _impl->setSimCardPower(in_serial, in_powerUp);
  }
  ::ndk::ScopedAStatus setUiccSubscription(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SelectUiccSub& in_uiccSub) override {
    return _impl->setUiccSubscription(in_serial, in_uiccSub);
  }
  ::ndk::ScopedAStatus supplyIccPin2ForApp(int32_t in_serial, const std::string& in_pin2, const std::string& in_aid) override {
    return _impl->supplyIccPin2ForApp(in_serial, in_pin2, in_aid);
  }
  ::ndk::ScopedAStatus supplyIccPinForApp(int32_t in_serial, const std::string& in_pin, const std::string& in_aid) override {
    return _impl->supplyIccPinForApp(in_serial, in_pin, in_aid);
  }
  ::ndk::ScopedAStatus supplyIccPuk2ForApp(int32_t in_serial, const std::string& in_puk2, const std::string& in_pin2, const std::string& in_aid) override {
    return _impl->supplyIccPuk2ForApp(in_serial, in_puk2, in_pin2, in_aid);
  }
  ::ndk::ScopedAStatus supplyIccPukForApp(int32_t in_serial, const std::string& in_puk, const std::string& in_pin, const std::string& in_aid) override {
    return _impl->supplyIccPukForApp(in_serial, in_puk, in_pin, in_aid);
  }
  ::ndk::ScopedAStatus supplySimDepersonalization(int32_t in_serial, ::aidl::android::hardware::radio::sim::PersoSubstate in_persoType, const std::string& in_controlKey) override {
    return _impl->supplySimDepersonalization(in_serial, in_persoType, in_controlKey);
  }
  ::ndk::ScopedAStatus updateSimPhonebookRecords(int32_t in_serial, const ::aidl::android::hardware::radio::sim::PhonebookRecordInfo& in_recordInfo) override {
    return _impl->updateSimPhonebookRecords(in_serial, in_recordInfo);
  }
protected:
private:
  std::shared_ptr<IRadioSim> _impl;
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
