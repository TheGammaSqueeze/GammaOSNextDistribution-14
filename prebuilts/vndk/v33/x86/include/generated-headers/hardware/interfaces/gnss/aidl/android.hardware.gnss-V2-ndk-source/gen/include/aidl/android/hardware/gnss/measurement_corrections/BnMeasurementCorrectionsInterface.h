#pragma once

#include "aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsInterface.h"

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
namespace measurement_corrections {
class BnMeasurementCorrectionsInterface : public ::ndk::BnCInterface<IMeasurementCorrectionsInterface> {
public:
  BnMeasurementCorrectionsInterface();
  virtual ~BnMeasurementCorrectionsInterface();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IMeasurementCorrectionsInterfaceDelegator : public BnMeasurementCorrectionsInterface {
public:
  explicit IMeasurementCorrectionsInterfaceDelegator(const std::shared_ptr<IMeasurementCorrectionsInterface> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IMeasurementCorrectionsInterface::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCorrections(const ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections& in_corrections) override {
    return _impl->setCorrections(in_corrections);
  }
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
protected:
private:
  std::shared_ptr<IMeasurementCorrectionsInterface> _impl;
};

}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
