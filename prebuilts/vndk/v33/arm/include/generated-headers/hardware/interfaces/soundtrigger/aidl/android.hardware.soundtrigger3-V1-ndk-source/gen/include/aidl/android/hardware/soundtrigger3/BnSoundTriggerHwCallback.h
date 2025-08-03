#pragma once

#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHwCallback.h"

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
namespace soundtrigger3 {
class BnSoundTriggerHwCallback : public ::ndk::BnCInterface<ISoundTriggerHwCallback> {
public:
  BnSoundTriggerHwCallback();
  virtual ~BnSoundTriggerHwCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISoundTriggerHwCallbackDelegator : public BnSoundTriggerHwCallback {
public:
  explicit ISoundTriggerHwCallbackDelegator(const std::shared_ptr<ISoundTriggerHwCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISoundTriggerHwCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus modelUnloaded(int32_t in_model) override {
    return _impl->modelUnloaded(in_model);
  }
  ::ndk::ScopedAStatus phraseRecognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::PhraseRecognitionEvent& in_event) override {
    return _impl->phraseRecognitionCallback(in_model, in_event);
  }
  ::ndk::ScopedAStatus recognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::RecognitionEvent& in_event) override {
    return _impl->recognitionCallback(in_model, in_event);
  }
protected:
private:
  std::shared_ptr<ISoundTriggerHwCallback> _impl;
};

}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
