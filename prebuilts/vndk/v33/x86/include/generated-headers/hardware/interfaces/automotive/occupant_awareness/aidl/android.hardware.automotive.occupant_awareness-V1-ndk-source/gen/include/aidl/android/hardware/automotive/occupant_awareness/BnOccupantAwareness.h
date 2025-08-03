#pragma once

#include "aidl/android/hardware/automotive/occupant_awareness/IOccupantAwareness.h"

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
class BnOccupantAwareness : public ::ndk::BnCInterface<IOccupantAwareness> {
public:
  BnOccupantAwareness();
  virtual ~BnOccupantAwareness();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IOccupantAwarenessDelegator : public BnOccupantAwareness {
public:
  explicit IOccupantAwarenessDelegator(const std::shared_ptr<IOccupantAwareness> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IOccupantAwareness::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus startDetection(::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus* _aidl_return) override {
    return _impl->startDetection(_aidl_return);
  }
  ::ndk::ScopedAStatus stopDetection(::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus* _aidl_return) override {
    return _impl->stopDetection(_aidl_return);
  }
  ::ndk::ScopedAStatus getCapabilityForRole(::aidl::android::hardware::automotive::occupant_awareness::Role in_occupantRole, int32_t* _aidl_return) override {
    return _impl->getCapabilityForRole(in_occupantRole, _aidl_return);
  }
  ::ndk::ScopedAStatus getState(::aidl::android::hardware::automotive::occupant_awareness::Role in_occupantRole, int32_t in_detectionCapability, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus* _aidl_return) override {
    return _impl->getState(in_occupantRole, in_detectionCapability, _aidl_return);
  }
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::automotive::occupant_awareness::IOccupantAwarenessClientCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus getLatestDetection(::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections* out_detections) override {
    return _impl->getLatestDetection(out_detections);
  }
protected:
private:
  std::shared_ptr<IOccupantAwareness> _impl;
};

}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
