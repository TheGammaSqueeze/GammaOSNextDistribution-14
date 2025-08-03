#pragma once

#include "aidl/android/hardware/radio/sim/IRadioSimIndication.h"

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
class BnRadioSimIndication : public ::ndk::BnCInterface<IRadioSimIndication> {
public:
  BnRadioSimIndication();
  virtual ~BnRadioSimIndication();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioSimIndicationDelegator : public BnRadioSimIndication {
public:
  explicit IRadioSimIndicationDelegator(const std::shared_ptr<IRadioSimIndication> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioSimIndication::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus carrierInfoForImsiEncryption(::aidl::android::hardware::radio::RadioIndicationType in_info) override {
    return _impl->carrierInfoForImsiEncryption(in_info);
  }
  ::ndk::ScopedAStatus cdmaSubscriptionSourceChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_cdmaSource) override {
    return _impl->cdmaSubscriptionSourceChanged(in_type, in_cdmaSource);
  }
  ::ndk::ScopedAStatus simPhonebookChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->simPhonebookChanged(in_type);
  }
  ::ndk::ScopedAStatus simPhonebookRecordsReceived(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::sim::PbReceivedStatus in_status, const std::vector<::aidl::android::hardware::radio::sim::PhonebookRecordInfo>& in_records) override {
    return _impl->simPhonebookRecordsReceived(in_type, in_status, in_records);
  }
  ::ndk::ScopedAStatus simRefresh(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::sim::SimRefreshResult& in_refreshResult) override {
    return _impl->simRefresh(in_type, in_refreshResult);
  }
  ::ndk::ScopedAStatus simStatusChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->simStatusChanged(in_type);
  }
  ::ndk::ScopedAStatus stkEventNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_cmd) override {
    return _impl->stkEventNotify(in_type, in_cmd);
  }
  ::ndk::ScopedAStatus stkProactiveCommand(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_cmd) override {
    return _impl->stkProactiveCommand(in_type, in_cmd);
  }
  ::ndk::ScopedAStatus stkSessionEnd(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->stkSessionEnd(in_type);
  }
  ::ndk::ScopedAStatus subscriptionStatusChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_activate) override {
    return _impl->subscriptionStatusChanged(in_type, in_activate);
  }
  ::ndk::ScopedAStatus uiccApplicationsEnablementChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_enabled) override {
    return _impl->uiccApplicationsEnablementChanged(in_type, in_enabled);
  }
protected:
private:
  std::shared_ptr<IRadioSimIndication> _impl;
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
