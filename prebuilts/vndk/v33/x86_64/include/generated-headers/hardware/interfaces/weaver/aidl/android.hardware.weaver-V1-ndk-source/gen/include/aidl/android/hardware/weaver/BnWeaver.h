#pragma once

#include "aidl/android/hardware/weaver/IWeaver.h"

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
namespace weaver {
class BnWeaver : public ::ndk::BnCInterface<IWeaver> {
public:
  BnWeaver();
  virtual ~BnWeaver();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IWeaverDelegator : public BnWeaver {
public:
  explicit IWeaverDelegator(const std::shared_ptr<IWeaver> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IWeaver::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::weaver::WeaverConfig* _aidl_return) override {
    return _impl->getConfig(_aidl_return);
  }
  ::ndk::ScopedAStatus read(int32_t in_slotId, const std::vector<uint8_t>& in_key, ::aidl::android::hardware::weaver::WeaverReadResponse* _aidl_return) override {
    return _impl->read(in_slotId, in_key, _aidl_return);
  }
  ::ndk::ScopedAStatus write(int32_t in_slotId, const std::vector<uint8_t>& in_key, const std::vector<uint8_t>& in_value) override {
    return _impl->write(in_slotId, in_key, in_value);
  }
protected:
private:
  std::shared_ptr<IWeaver> _impl;
};

}  // namespace weaver
}  // namespace hardware
}  // namespace android
}  // namespace aidl
