#pragma once

#include "aidl/android/hardware/ir/IConsumerIr.h"

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
namespace ir {
class BnConsumerIr : public ::ndk::BnCInterface<IConsumerIr> {
public:
  BnConsumerIr();
  virtual ~BnConsumerIr();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IConsumerIrDelegator : public BnConsumerIr {
public:
  explicit IConsumerIrDelegator(const std::shared_ptr<IConsumerIr> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IConsumerIr::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getCarrierFreqs(std::vector<::aidl::android::hardware::ir::ConsumerIrFreqRange>* _aidl_return) override {
    return _impl->getCarrierFreqs(_aidl_return);
  }
  ::ndk::ScopedAStatus transmit(int32_t in_carrierFreqHz, const std::vector<int32_t>& in_pattern) override {
    return _impl->transmit(in_carrierFreqHz, in_pattern);
  }
protected:
private:
  std::shared_ptr<IConsumerIr> _impl;
};

}  // namespace ir
}  // namespace hardware
}  // namespace android
}  // namespace aidl
