#pragma once

#include "aidl/android/hardware/radio/network/IRadioNetworkIndication.h"

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
namespace network {
class BnRadioNetworkIndication : public ::ndk::BnCInterface<IRadioNetworkIndication> {
public:
  BnRadioNetworkIndication();
  virtual ~BnRadioNetworkIndication();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioNetworkIndicationDelegator : public BnRadioNetworkIndication {
public:
  explicit IRadioNetworkIndicationDelegator(const std::shared_ptr<IRadioNetworkIndication> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioNetworkIndication::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus barringInfoChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) override {
    return _impl->barringInfoChanged(in_type, in_cellIdentity, in_barringInfos);
  }
  ::ndk::ScopedAStatus cdmaPrlChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_version) override {
    return _impl->cdmaPrlChanged(in_type, in_version);
  }
  ::ndk::ScopedAStatus cellInfoList(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_records) override {
    return _impl->cellInfoList(in_type, in_records);
  }
  ::ndk::ScopedAStatus currentLinkCapacityEstimate(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::LinkCapacityEstimate& in_lce) override {
    return _impl->currentLinkCapacityEstimate(in_type, in_lce);
  }
  ::ndk::ScopedAStatus currentPhysicalChannelConfigs(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::network::PhysicalChannelConfig>& in_configs) override {
    return _impl->currentPhysicalChannelConfigs(in_type, in_configs);
  }
  ::ndk::ScopedAStatus currentSignalStrength(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) override {
    return _impl->currentSignalStrength(in_type, in_signalStrength);
  }
  ::ndk::ScopedAStatus imsNetworkStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->imsNetworkStateChanged(in_type);
  }
  ::ndk::ScopedAStatus networkScanResult(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::NetworkScanResult& in_result) override {
    return _impl->networkScanResult(in_type, in_result);
  }
  ::ndk::ScopedAStatus networkStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->networkStateChanged(in_type);
  }
  ::ndk::ScopedAStatus nitzTimeReceived(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_nitzTime, int64_t in_receivedTimeMs, int64_t in_ageMs) override {
    return _impl->nitzTimeReceived(in_type, in_nitzTime, in_receivedTimeMs, in_ageMs);
  }
  ::ndk::ScopedAStatus registrationFailed(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::string& in_chosenPlmn, int32_t in_domain, int32_t in_causeCode, int32_t in_additionalCauseCode) override {
    return _impl->registrationFailed(in_type, in_cellIdentity, in_chosenPlmn, in_domain, in_causeCode, in_additionalCauseCode);
  }
  ::ndk::ScopedAStatus restrictedStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::network::PhoneRestrictedState in_state) override {
    return _impl->restrictedStateChanged(in_type, in_state);
  }
  ::ndk::ScopedAStatus suppSvcNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::SuppSvcNotification& in_suppSvc) override {
    return _impl->suppSvcNotify(in_type, in_suppSvc);
  }
  ::ndk::ScopedAStatus voiceRadioTechChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::RadioTechnology in_rat) override {
    return _impl->voiceRadioTechChanged(in_type, in_rat);
  }
protected:
private:
  std::shared_ptr<IRadioNetworkIndication> _impl;
};

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
