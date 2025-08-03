#pragma once

#include "aidl/android/hardware/graphics/composer3/IComposerCallback.h"

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
class BnComposerCallback : public ::ndk::BnCInterface<IComposerCallback> {
public:
  BnComposerCallback();
  virtual ~BnComposerCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IComposerCallbackDelegator : public BnComposerCallback {
public:
  explicit IComposerCallbackDelegator(const std::shared_ptr<IComposerCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IComposerCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onHotplug(int64_t in_display, bool in_connected) override {
    return _impl->onHotplug(in_display, in_connected);
  }
  ::ndk::ScopedAStatus onRefresh(int64_t in_display) override {
    return _impl->onRefresh(in_display);
  }
  ::ndk::ScopedAStatus onSeamlessPossible(int64_t in_display) override {
    return _impl->onSeamlessPossible(in_display);
  }
  ::ndk::ScopedAStatus onVsync(int64_t in_display, int64_t in_timestamp, int32_t in_vsyncPeriodNanos) override {
    return _impl->onVsync(in_display, in_timestamp, in_vsyncPeriodNanos);
  }
  ::ndk::ScopedAStatus onVsyncPeriodTimingChanged(int64_t in_display, const ::aidl::android::hardware::graphics::composer3::VsyncPeriodChangeTimeline& in_updatedTimeline) override {
    return _impl->onVsyncPeriodTimingChanged(in_display, in_updatedTimeline);
  }
  ::ndk::ScopedAStatus onVsyncIdle(int64_t in_display) override {
    return _impl->onVsyncIdle(in_display);
  }
protected:
private:
  std::shared_ptr<IComposerCallback> _impl;
};

}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
