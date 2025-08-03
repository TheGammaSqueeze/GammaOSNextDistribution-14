#pragma once

#include "aidl/android/hardware/radio/data/IRadioDataIndication.h"

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
class BnRadioDataIndication : public ::ndk::BnCInterface<IRadioDataIndication> {
public:
  BnRadioDataIndication();
  virtual ~BnRadioDataIndication();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioDataIndicationDelegator : public BnRadioDataIndication {
public:
  explicit IRadioDataIndicationDelegator(const std::shared_ptr<IRadioDataIndication> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioDataIndication::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus dataCallListChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcList) override {
    return _impl->dataCallListChanged(in_type, in_dcList);
  }
  ::ndk::ScopedAStatus keepaliveStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) override {
    return _impl->keepaliveStatus(in_type, in_status);
  }
  ::ndk::ScopedAStatus pcoData(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::PcoDataInfo& in_pco) override {
    return _impl->pcoData(in_type, in_pco);
  }
  ::ndk::ScopedAStatus unthrottleApn(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo) override {
    return _impl->unthrottleApn(in_type, in_dataProfileInfo);
  }
  ::ndk::ScopedAStatus slicingConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) override {
    return _impl->slicingConfigChanged(in_type, in_slicingConfig);
  }
protected:
private:
  std::shared_ptr<IRadioDataIndication> _impl;
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
