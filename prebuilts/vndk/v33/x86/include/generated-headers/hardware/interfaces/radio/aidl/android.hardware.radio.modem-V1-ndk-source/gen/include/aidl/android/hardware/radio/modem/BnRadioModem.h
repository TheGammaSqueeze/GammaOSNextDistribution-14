#pragma once

#include "aidl/android/hardware/radio/modem/IRadioModem.h"

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
namespace modem {
class BnRadioModem : public ::ndk::BnCInterface<IRadioModem> {
public:
  BnRadioModem();
  virtual ~BnRadioModem();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioModemDelegator : public BnRadioModem {
public:
  explicit IRadioModemDelegator(const std::shared_ptr<IRadioModem> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioModem::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus enableModem(int32_t in_serial, bool in_on) override {
    return _impl->enableModem(in_serial, in_on);
  }
  ::ndk::ScopedAStatus getBasebandVersion(int32_t in_serial) override {
    return _impl->getBasebandVersion(in_serial);
  }
  ::ndk::ScopedAStatus getDeviceIdentity(int32_t in_serial) override {
    return _impl->getDeviceIdentity(in_serial);
  }
  ::ndk::ScopedAStatus getHardwareConfig(int32_t in_serial) override {
    return _impl->getHardwareConfig(in_serial);
  }
  ::ndk::ScopedAStatus getModemActivityInfo(int32_t in_serial) override {
    return _impl->getModemActivityInfo(in_serial);
  }
  ::ndk::ScopedAStatus getModemStackStatus(int32_t in_serial) override {
    return _impl->getModemStackStatus(in_serial);
  }
  ::ndk::ScopedAStatus getRadioCapability(int32_t in_serial) override {
    return _impl->getRadioCapability(in_serial);
  }
  ::ndk::ScopedAStatus nvReadItem(int32_t in_serial, ::aidl::android::hardware::radio::modem::NvItem in_itemId) override {
    return _impl->nvReadItem(in_serial, in_itemId);
  }
  ::ndk::ScopedAStatus nvResetConfig(int32_t in_serial, ::aidl::android::hardware::radio::modem::ResetNvType in_resetType) override {
    return _impl->nvResetConfig(in_serial, in_resetType);
  }
  ::ndk::ScopedAStatus nvWriteCdmaPrl(int32_t in_serial, const std::vector<uint8_t>& in_prl) override {
    return _impl->nvWriteCdmaPrl(in_serial, in_prl);
  }
  ::ndk::ScopedAStatus nvWriteItem(int32_t in_serial, const ::aidl::android::hardware::radio::modem::NvWriteItem& in_item) override {
    return _impl->nvWriteItem(in_serial, in_item);
  }
  ::ndk::ScopedAStatus requestShutdown(int32_t in_serial) override {
    return _impl->requestShutdown(in_serial);
  }
  ::ndk::ScopedAStatus responseAcknowledgement() override {
    return _impl->responseAcknowledgement();
  }
  ::ndk::ScopedAStatus sendDeviceState(int32_t in_serial, ::aidl::android::hardware::radio::modem::DeviceStateType in_deviceStateType, bool in_state) override {
    return _impl->sendDeviceState(in_serial, in_deviceStateType, in_state);
  }
  ::ndk::ScopedAStatus setRadioCapability(int32_t in_serial, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override {
    return _impl->setRadioCapability(in_serial, in_rc);
  }
  ::ndk::ScopedAStatus setRadioPower(int32_t in_serial, bool in_powerOn, bool in_forEmergencyCall, bool in_preferredForEmergencyCall) override {
    return _impl->setRadioPower(in_serial, in_powerOn, in_forEmergencyCall, in_preferredForEmergencyCall);
  }
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::modem::IRadioModemResponse>& in_radioModemResponse, const std::shared_ptr<::aidl::android::hardware::radio::modem::IRadioModemIndication>& in_radioModemIndication) override {
    return _impl->setResponseFunctions(in_radioModemResponse, in_radioModemIndication);
  }
protected:
private:
  std::shared_ptr<IRadioModem> _impl;
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
