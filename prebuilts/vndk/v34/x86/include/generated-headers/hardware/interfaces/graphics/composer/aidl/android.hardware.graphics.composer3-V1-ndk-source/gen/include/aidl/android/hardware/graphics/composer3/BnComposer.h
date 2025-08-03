#pragma once

#include "aidl/android/hardware/graphics/composer3/IComposer.h"

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
namespace graphics {
namespace composer3 {
class BnComposer : public ::ndk::BnCInterface<IComposer> {
public:
  BnComposer();
  virtual ~BnComposer();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IComposerDelegator : public BnComposer {
public:
  explicit IComposerDelegator(const std::shared_ptr<IComposer> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IComposer::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus createClient(std::shared_ptr<::aidl::android::hardware::graphics::composer3::IComposerClient>* _aidl_return) override {
    return _impl->createClient(_aidl_return);
  }
  ::ndk::ScopedAStatus getCapabilities(std::vector<::aidl::android::hardware::graphics::composer3::Capability>* _aidl_return) override {
    return _impl->getCapabilities(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IComposer> _impl;
};

}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
