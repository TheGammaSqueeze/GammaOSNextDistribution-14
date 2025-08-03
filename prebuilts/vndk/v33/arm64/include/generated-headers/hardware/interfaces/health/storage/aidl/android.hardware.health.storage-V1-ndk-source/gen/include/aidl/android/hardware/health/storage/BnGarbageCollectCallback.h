#pragma once

#include "aidl/android/hardware/health/storage/IGarbageCollectCallback.h"

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
namespace health {
namespace storage {
class BnGarbageCollectCallback : public ::ndk::BnCInterface<IGarbageCollectCallback> {
public:
  BnGarbageCollectCallback();
  virtual ~BnGarbageCollectCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGarbageCollectCallbackDelegator : public BnGarbageCollectCallback {
public:
  explicit IGarbageCollectCallbackDelegator(const std::shared_ptr<IGarbageCollectCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGarbageCollectCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onFinish(::aidl::android::hardware::health::storage::Result in_result) override {
    return _impl->onFinish(in_result);
  }
protected:
private:
  std::shared_ptr<IGarbageCollectCallback> _impl;
};

}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
