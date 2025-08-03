#pragma once

#include "aidl/android/hardware/gnss/IGnssBatchingCallback.h"

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
class BnGnssBatchingCallback : public ::ndk::BnCInterface<IGnssBatchingCallback> {
public:
  BnGnssBatchingCallback();
  virtual ~BnGnssBatchingCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssBatchingCallbackDelegator : public BnGnssBatchingCallback {
public:
  explicit IGnssBatchingCallbackDelegator(const std::shared_ptr<IGnssBatchingCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssBatchingCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus gnssLocationBatchCb(const std::vector<::aidl::android::hardware::gnss::GnssLocation>& in_locations) override {
    return _impl->gnssLocationBatchCb(in_locations);
  }
protected:
private:
  std::shared_ptr<IGnssBatchingCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
