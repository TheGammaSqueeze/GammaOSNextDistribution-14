#pragma once

#include "aidl/android/hardware/uwb/IUwb.h"

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
namespace uwb {
class BnUwb : public ::ndk::BnCInterface<IUwb> {
public:
  BnUwb();
  virtual ~BnUwb();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IUwbDelegator : public BnUwb {
public:
  explicit IUwbDelegator(const std::shared_ptr<IUwb> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IUwb::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getChips(std::vector<std::string>* _aidl_return) override {
    return _impl->getChips(_aidl_return);
  }
  ::ndk::ScopedAStatus getChip(const std::string& in_name, std::shared_ptr<::aidl::android::hardware::uwb::IUwbChip>* _aidl_return) override {
    return _impl->getChip(in_name, _aidl_return);
  }
protected:
private:
  std::shared_ptr<IUwb> _impl;
};

}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
