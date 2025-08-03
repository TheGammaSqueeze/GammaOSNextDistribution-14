#pragma once

#include "aidl/android/hardware/gnss/IGnssMeasurementInterface.h"

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
namespace gnss {
class BnGnssMeasurementInterface : public ::ndk::BnCInterface<IGnssMeasurementInterface> {
public:
  BnGnssMeasurementInterface();
  virtual ~BnGnssMeasurementInterface();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssMeasurementInterfaceDelegator : public BnGnssMeasurementInterface {
public:
  explicit IGnssMeasurementInterfaceDelegator(const std::shared_ptr<IGnssMeasurementInterface> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssMeasurementInterface::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, bool in_enableFullTracking, bool in_enableCorrVecOutputs) override {
    return _impl->setCallback(in_callback, in_enableFullTracking, in_enableCorrVecOutputs);
  }
  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
  ::ndk::ScopedAStatus setCallbackWithOptions(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, const ::aidl::android::hardware::gnss::IGnssMeasurementInterface::Options& in_options) override {
    return _impl->setCallbackWithOptions(in_callback, in_options);
  }
protected:
private:
  std::shared_ptr<IGnssMeasurementInterface> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
