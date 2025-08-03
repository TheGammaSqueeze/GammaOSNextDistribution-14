#pragma once

#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHw.h"

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
class BnSoundTriggerHw : public ::ndk::BnCInterface<ISoundTriggerHw> {
public:
  BnSoundTriggerHw();
  virtual ~BnSoundTriggerHw();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISoundTriggerHwDelegator : public BnSoundTriggerHw {
public:
  explicit ISoundTriggerHwDelegator(const std::shared_ptr<ISoundTriggerHw> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISoundTriggerHw::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getProperties(::aidl::android::media::soundtrigger::Properties* _aidl_return) override {
    return _impl->getProperties(_aidl_return);
  }
  ::ndk::ScopedAStatus registerGlobalCallback(const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwGlobalCallback>& in_callback) override {
    return _impl->registerGlobalCallback(in_callback);
  }
  ::ndk::ScopedAStatus loadSoundModel(const ::aidl::android::media::soundtrigger::SoundModel& in_soundModel, const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwCallback>& in_callback, int32_t* _aidl_return) override {
    return _impl->loadSoundModel(in_soundModel, in_callback, _aidl_return);
  }
  ::ndk::ScopedAStatus loadPhraseSoundModel(const ::aidl::android::media::soundtrigger::PhraseSoundModel& in_soundModel, const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwCallback>& in_callback, int32_t* _aidl_return) override {
    return _impl->loadPhraseSoundModel(in_soundModel, in_callback, _aidl_return);
  }
  ::ndk::ScopedAStatus unloadSoundModel(int32_t in_modelHandle) override {
    return _impl->unloadSoundModel(in_modelHandle);
  }
  ::ndk::ScopedAStatus startRecognition(int32_t in_modelHandle, int32_t in_deviceHandle, int32_t in_ioHandle, const ::aidl::android::media::soundtrigger::RecognitionConfig& in_config) override {
    return _impl->startRecognition(in_modelHandle, in_deviceHandle, in_ioHandle, in_config);
  }
  ::ndk::ScopedAStatus stopRecognition(int32_t in_modelHandle) override {
    return _impl->stopRecognition(in_modelHandle);
  }
  ::ndk::ScopedAStatus forceRecognitionEvent(int32_t in_modelHandle) override {
    return _impl->forceRecognitionEvent(in_modelHandle);
  }
  ::ndk::ScopedAStatus queryParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, std::optional<::aidl::android::media::soundtrigger::ModelParameterRange>* _aidl_return) override {
    return _impl->queryParameter(in_modelHandle, in_modelParam, _aidl_return);
  }
  ::ndk::ScopedAStatus getParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, int32_t* _aidl_return) override {
    return _impl->getParameter(in_modelHandle, in_modelParam, _aidl_return);
  }
  ::ndk::ScopedAStatus setParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, int32_t in_value) override {
    return _impl->setParameter(in_modelHandle, in_modelParam, in_value);
  }
protected:
private:
  std::shared_ptr<ISoundTriggerHw> _impl;
};

}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
