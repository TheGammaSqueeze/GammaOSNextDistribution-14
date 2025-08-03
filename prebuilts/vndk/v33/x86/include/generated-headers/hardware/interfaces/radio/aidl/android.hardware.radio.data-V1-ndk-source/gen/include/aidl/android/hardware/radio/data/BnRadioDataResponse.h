#pragma once

#include "aidl/android/hardware/radio/data/IRadioDataResponse.h"

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
class BnRadioDataResponse : public ::ndk::BnCInterface<IRadioDataResponse> {
public:
  BnRadioDataResponse();
  virtual ~BnRadioDataResponse();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioDataResponseDelegator : public BnRadioDataResponse {
public:
  explicit IRadioDataResponseDelegator(const std::shared_ptr<IRadioDataResponse> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioDataResponse::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override {
    return _impl->acknowledgeRequest(in_serial);
  }
  ::ndk::ScopedAStatus allocatePduSessionIdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_id) override {
    return _impl->allocatePduSessionIdResponse(in_info, in_id);
  }
  ::ndk::ScopedAStatus cancelHandoverResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->cancelHandoverResponse(in_info);
  }
  ::ndk::ScopedAStatus deactivateDataCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->deactivateDataCallResponse(in_info);
  }
  ::ndk::ScopedAStatus getDataCallListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcResponse) override {
    return _impl->getDataCallListResponse(in_info, in_dcResponse);
  }
  ::ndk::ScopedAStatus getSlicingConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) override {
    return _impl->getSlicingConfigResponse(in_info, in_slicingConfig);
  }
  ::ndk::ScopedAStatus releasePduSessionIdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->releasePduSessionIdResponse(in_info);
  }
  ::ndk::ScopedAStatus setDataAllowedResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setDataAllowedResponse(in_info);
  }
  ::ndk::ScopedAStatus setDataProfileResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setDataProfileResponse(in_info);
  }
  ::ndk::ScopedAStatus setDataThrottlingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setDataThrottlingResponse(in_info);
  }
  ::ndk::ScopedAStatus setInitialAttachApnResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setInitialAttachApnResponse(in_info);
  }
  ::ndk::ScopedAStatus setupDataCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::SetupDataCallResult& in_dcResponse) override {
    return _impl->setupDataCallResponse(in_info, in_dcResponse);
  }
  ::ndk::ScopedAStatus startHandoverResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->startHandoverResponse(in_info);
  }
  ::ndk::ScopedAStatus startKeepaliveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) override {
    return _impl->startKeepaliveResponse(in_info, in_status);
  }
  ::ndk::ScopedAStatus stopKeepaliveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->stopKeepaliveResponse(in_info);
  }
protected:
private:
  std::shared_ptr<IRadioDataResponse> _impl;
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
