#pragma once

#include "aidl/android/hardware/nfc/INfc.h"

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
namespace nfc {
class BnNfc : public ::ndk::BnCInterface<INfc> {
public:
  BnNfc();
  virtual ~BnNfc();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class INfcDelegator : public BnNfc {
public:
  explicit INfcDelegator(const std::shared_ptr<INfc> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != INfc::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::nfc::INfcClientCallback>& in_clientCallback) override {
    return _impl->open(in_clientCallback);
  }
  ::ndk::ScopedAStatus close(::aidl::android::hardware::nfc::NfcCloseType in_type) override {
    return _impl->close(in_type);
  }
  ::ndk::ScopedAStatus coreInitialized() override {
    return _impl->coreInitialized();
  }
  ::ndk::ScopedAStatus factoryReset() override {
    return _impl->factoryReset();
  }
  ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::nfc::NfcConfig* _aidl_return) override {
    return _impl->getConfig(_aidl_return);
  }
  ::ndk::ScopedAStatus powerCycle() override {
    return _impl->powerCycle();
  }
  ::ndk::ScopedAStatus preDiscover() override {
    return _impl->preDiscover();
  }
  ::ndk::ScopedAStatus write(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) override {
    return _impl->write(in_data, _aidl_return);
  }
  ::ndk::ScopedAStatus setEnableVerboseLogging(bool in_enable) override {
    return _impl->setEnableVerboseLogging(in_enable);
  }
  ::ndk::ScopedAStatus isVerboseLoggingEnabled(bool* _aidl_return) override {
    return _impl->isVerboseLoggingEnabled(_aidl_return);
  }
protected:
private:
  std::shared_ptr<INfc> _impl;
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
