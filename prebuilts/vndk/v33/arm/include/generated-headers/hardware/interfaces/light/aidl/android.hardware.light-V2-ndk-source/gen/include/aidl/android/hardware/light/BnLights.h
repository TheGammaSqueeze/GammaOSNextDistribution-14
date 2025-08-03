#pragma once

#include "aidl/android/hardware/light/ILights.h"

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
namespace light {
class BnLights : public ::ndk::BnCInterface<ILights> {
public:
  BnLights();
  virtual ~BnLights();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ILightsDelegator : public BnLights {
public:
  explicit ILightsDelegator(const std::shared_ptr<ILights> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ILights::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setLightState(int32_t in_id, const ::aidl::android::hardware::light::HwLightState& in_state) override {
    return _impl->setLightState(in_id, in_state);
  }
  ::ndk::ScopedAStatus getLights(std::vector<::aidl::android::hardware::light::HwLight>* _aidl_return) override {
    return _impl->getLights(_aidl_return);
  }
protected:
private:
  std::shared_ptr<ILights> _impl;
};

}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
