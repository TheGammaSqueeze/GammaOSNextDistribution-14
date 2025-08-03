#pragma once

#include "aidl/android/hardware/automotive/occupant_awareness/IOccupantAwarenessClientCallback.h"

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
namespace automotive {
namespace occupant_awareness {
class BnOccupantAwarenessClientCallback : public ::ndk::BnCInterface<IOccupantAwarenessClientCallback> {
public:
  BnOccupantAwarenessClientCallback();
  virtual ~BnOccupantAwarenessClientCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IOccupantAwarenessClientCallbackDelegator : public BnOccupantAwarenessClientCallback {
public:
  explicit IOccupantAwarenessClientCallbackDelegator(const std::shared_ptr<IOccupantAwarenessClientCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IOccupantAwarenessClientCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onSystemStatusChanged(int32_t in_detectionFlags, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus in_status) override {
    return _impl->onSystemStatusChanged(in_detectionFlags, in_status);
  }
  ::ndk::ScopedAStatus onDetectionEvent(const ::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections& in_detections) override {
    return _impl->onDetectionEvent(in_detections);
  }
protected:
private:
  std::shared_ptr<IOccupantAwarenessClientCallback> _impl;
};

}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
