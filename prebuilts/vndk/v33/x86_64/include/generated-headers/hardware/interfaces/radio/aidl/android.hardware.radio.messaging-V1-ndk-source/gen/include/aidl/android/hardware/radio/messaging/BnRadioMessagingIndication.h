#pragma once

#include "aidl/android/hardware/radio/messaging/IRadioMessagingIndication.h"

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
class BnRadioMessagingIndication : public ::ndk::BnCInterface<IRadioMessagingIndication> {
public:
  BnRadioMessagingIndication();
  virtual ~BnRadioMessagingIndication();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioMessagingIndicationDelegator : public BnRadioMessagingIndication {
public:
  explicit IRadioMessagingIndicationDelegator(const std::shared_ptr<IRadioMessagingIndication> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioMessagingIndication::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus cdmaNewSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::messaging::CdmaSmsMessage& in_msg) override {
    return _impl->cdmaNewSms(in_type, in_msg);
  }
  ::ndk::ScopedAStatus cdmaRuimSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->cdmaRuimSmsStorageFull(in_type);
  }
  ::ndk::ScopedAStatus newBroadcastSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_data) override {
    return _impl->newBroadcastSms(in_type, in_data);
  }
  ::ndk::ScopedAStatus newSms(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) override {
    return _impl->newSms(in_type, in_pdu);
  }
  ::ndk::ScopedAStatus newSmsOnSim(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_recordNumber) override {
    return _impl->newSmsOnSim(in_type, in_recordNumber);
  }
  ::ndk::ScopedAStatus newSmsStatusReport(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<uint8_t>& in_pdu) override {
    return _impl->newSmsStatusReport(in_type, in_pdu);
  }
  ::ndk::ScopedAStatus simSmsStorageFull(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->simSmsStorageFull(in_type);
  }
protected:
private:
  std::shared_ptr<IRadioMessagingIndication> _impl;
};

}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
