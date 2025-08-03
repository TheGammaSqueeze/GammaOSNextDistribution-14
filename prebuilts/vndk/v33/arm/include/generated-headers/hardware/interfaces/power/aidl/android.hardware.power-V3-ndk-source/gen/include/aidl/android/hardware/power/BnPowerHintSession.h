#pragma once

#include "aidl/android/hardware/power/IPowerHintSession.h"

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
namespace power {
class BnPowerHintSession : public ::ndk::BnCInterface<IPowerHintSession> {
public:
  BnPowerHintSession();
  virtual ~BnPowerHintSession();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IPowerHintSessionDelegator : public BnPowerHintSession {
public:
  explicit IPowerHintSessionDelegator(const std::shared_ptr<IPowerHintSession> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IPowerHintSession::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus updateTargetWorkDuration(int64_t in_targetDurationNanos) override {
    return _impl->updateTargetWorkDuration(in_targetDurationNanos);
  }
  ::ndk::ScopedAStatus reportActualWorkDuration(const std::vector<::aidl::android::hardware::power::WorkDuration>& in_durations) override {
    return _impl->reportActualWorkDuration(in_durations);
  }
  ::ndk::ScopedAStatus pause() override {
    return _impl->pause();
  }
  ::ndk::ScopedAStatus resume() override {
    return _impl->resume();
  }
  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
protected:
private:
  std::shared_ptr<IPowerHintSession> _impl;
};

}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
