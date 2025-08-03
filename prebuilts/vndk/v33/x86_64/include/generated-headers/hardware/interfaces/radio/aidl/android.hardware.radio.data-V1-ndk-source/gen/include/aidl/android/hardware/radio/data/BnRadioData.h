#pragma once

#include "aidl/android/hardware/radio/data/IRadioData.h"

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
namespace data {
class BnRadioData : public ::ndk::BnCInterface<IRadioData> {
public:
  BnRadioData();
  virtual ~BnRadioData();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioDataDelegator : public BnRadioData {
public:
  explicit IRadioDataDelegator(const std::shared_ptr<IRadioData> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioData::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus allocatePduSessionId(int32_t in_serial) override {
    return _impl->allocatePduSessionId(in_serial);
  }
  ::ndk::ScopedAStatus cancelHandover(int32_t in_serial, int32_t in_callId) override {
    return _impl->cancelHandover(in_serial, in_callId);
  }
  ::ndk::ScopedAStatus deactivateDataCall(int32_t in_serial, int32_t in_cid, ::aidl::android::hardware::radio::data::DataRequestReason in_reason) override {
    return _impl->deactivateDataCall(in_serial, in_cid, in_reason);
  }
  ::ndk::ScopedAStatus getDataCallList(int32_t in_serial) override {
    return _impl->getDataCallList(in_serial);
  }
  ::ndk::ScopedAStatus getSlicingConfig(int32_t in_serial) override {
    return _impl->getSlicingConfig(in_serial);
  }
  ::ndk::ScopedAStatus releasePduSessionId(int32_t in_serial, int32_t in_id) override {
    return _impl->releasePduSessionId(in_serial, in_id);
  }
  ::ndk::ScopedAStatus responseAcknowledgement() override {
    return _impl->responseAcknowledgement();
  }
  ::ndk::ScopedAStatus setDataAllowed(int32_t in_serial, bool in_allow) override {
    return _impl->setDataAllowed(in_serial, in_allow);
  }
  ::ndk::ScopedAStatus setDataProfile(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::data::DataProfileInfo>& in_profiles) override {
    return _impl->setDataProfile(in_serial, in_profiles);
  }
  ::ndk::ScopedAStatus setDataThrottling(int32_t in_serial, ::aidl::android::hardware::radio::data::DataThrottlingAction in_dataThrottlingAction, int64_t in_completionDurationMillis) override {
    return _impl->setDataThrottling(in_serial, in_dataThrottlingAction, in_completionDurationMillis);
  }
  ::ndk::ScopedAStatus setInitialAttachApn(int32_t in_serial, const std::optional<::aidl::android::hardware::radio::data::DataProfileInfo>& in_dataProfileInfo) override {
    return _impl->setInitialAttachApn(in_serial, in_dataProfileInfo);
  }
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::data::IRadioDataResponse>& in_radioDataResponse, const std::shared_ptr<::aidl::android::hardware::radio::data::IRadioDataIndication>& in_radioDataIndication) override {
    return _impl->setResponseFunctions(in_radioDataResponse, in_radioDataIndication);
  }
  ::ndk::ScopedAStatus setupDataCall(int32_t in_serial, ::aidl::android::hardware::radio::AccessNetwork in_accessNetwork, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo, bool in_roamingAllowed, ::aidl::android::hardware::radio::data::DataRequestReason in_reason, const std::vector<::aidl::android::hardware::radio::data::LinkAddress>& in_addresses, const std::vector<std::string>& in_dnses, int32_t in_pduSessionId, const std::optional<::aidl::android::hardware::radio::data::SliceInfo>& in_sliceInfo, bool in_matchAllRuleAllowed) override {
    return _impl->setupDataCall(in_serial, in_accessNetwork, in_dataProfileInfo, in_roamingAllowed, in_reason, in_addresses, in_dnses, in_pduSessionId, in_sliceInfo, in_matchAllRuleAllowed);
  }
  ::ndk::ScopedAStatus startHandover(int32_t in_serial, int32_t in_callId) override {
    return _impl->startHandover(in_serial, in_callId);
  }
  ::ndk::ScopedAStatus startKeepalive(int32_t in_serial, const ::aidl::android::hardware::radio::data::KeepaliveRequest& in_keepalive) override {
    return _impl->startKeepalive(in_serial, in_keepalive);
  }
  ::ndk::ScopedAStatus stopKeepalive(int32_t in_serial, int32_t in_sessionHandle) override {
    return _impl->stopKeepalive(in_serial, in_sessionHandle);
  }
protected:
private:
  std::shared_ptr<IRadioData> _impl;
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
