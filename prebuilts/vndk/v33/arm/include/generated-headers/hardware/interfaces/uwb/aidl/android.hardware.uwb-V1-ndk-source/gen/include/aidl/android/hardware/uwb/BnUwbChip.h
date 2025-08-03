#pragma once

#include "aidl/android/hardware/uwb/IUwbChip.h"

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
class BnUwbChip : public ::ndk::BnCInterface<IUwbChip> {
public:
  BnUwbChip();
  virtual ~BnUwbChip();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IUwbChipDelegator : public BnUwbChip {
public:
  explicit IUwbChipDelegator(const std::shared_ptr<IUwbChip> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IUwbChip::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override {
    return _impl->getName(_aidl_return);
  }
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::uwb::IUwbClientCallback>& in_clientCallback) override {
    return _impl->open(in_clientCallback);
  }
  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
  ::ndk::ScopedAStatus coreInit() override {
    return _impl->coreInit();
  }
  ::ndk::ScopedAStatus sessionInit(int32_t in_sessionId) override {
    return _impl->sessionInit(in_sessionId);
  }
  ::ndk::ScopedAStatus getSupportedAndroidUciVersion(int32_t* _aidl_return) override {
    return _impl->getSupportedAndroidUciVersion(_aidl_return);
  }
  ::ndk::ScopedAStatus sendUciMessage(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) override {
    return _impl->sendUciMessage(in_data, _aidl_return);
  }
protected:
private:
  std::shared_ptr<IUwbChip> _impl;
};

}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
