#pragma once

#include "aidl/android/hardware/vibrator/IVibrator.h"

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
namespace vibrator {
class BnVibrator : public ::ndk::BnCInterface<IVibrator> {
public:
  BnVibrator();
  virtual ~BnVibrator();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IVibratorDelegator : public BnVibrator {
public:
  explicit IVibratorDelegator(const std::shared_ptr<IVibrator> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IVibrator::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) override {
    return _impl->getCapabilities(_aidl_return);
  }
  ::ndk::ScopedAStatus off() override {
    return _impl->off();
  }
  ::ndk::ScopedAStatus on(int32_t in_timeoutMs, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override {
    return _impl->on(in_timeoutMs, in_callback);
  }
  ::ndk::ScopedAStatus perform(::aidl::android::hardware::vibrator::Effect in_effect, ::aidl::android::hardware::vibrator::EffectStrength in_strength, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback, int32_t* _aidl_return) override {
    return _impl->perform(in_effect, in_strength, in_callback, _aidl_return);
  }
  ::ndk::ScopedAStatus getSupportedEffects(std::vector<::aidl::android::hardware::vibrator::Effect>* _aidl_return) override {
    return _impl->getSupportedEffects(_aidl_return);
  }
  ::ndk::ScopedAStatus setAmplitude(float in_amplitude) override {
    return _impl->setAmplitude(in_amplitude);
  }
  ::ndk::ScopedAStatus setExternalControl(bool in_enabled) override {
    return _impl->setExternalControl(in_enabled);
  }
  ::ndk::ScopedAStatus getCompositionDelayMax(int32_t* _aidl_return) override {
    return _impl->getCompositionDelayMax(_aidl_return);
  }
  ::ndk::ScopedAStatus getCompositionSizeMax(int32_t* _aidl_return) override {
    return _impl->getCompositionSizeMax(_aidl_return);
  }
  ::ndk::ScopedAStatus getSupportedPrimitives(std::vector<::aidl::android::hardware::vibrator::CompositePrimitive>* _aidl_return) override {
    return _impl->getSupportedPrimitives(_aidl_return);
  }
  ::ndk::ScopedAStatus getPrimitiveDuration(::aidl::android::hardware::vibrator::CompositePrimitive in_primitive, int32_t* _aidl_return) override {
    return _impl->getPrimitiveDuration(in_primitive, _aidl_return);
  }
  ::ndk::ScopedAStatus compose(const std::vector<::aidl::android::hardware::vibrator::CompositeEffect>& in_composite, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override {
    return _impl->compose(in_composite, in_callback);
  }
  ::ndk::ScopedAStatus getSupportedAlwaysOnEffects(std::vector<::aidl::android::hardware::vibrator::Effect>* _aidl_return) override {
    return _impl->getSupportedAlwaysOnEffects(_aidl_return);
  }
  ::ndk::ScopedAStatus alwaysOnEnable(int32_t in_id, ::aidl::android::hardware::vibrator::Effect in_effect, ::aidl::android::hardware::vibrator::EffectStrength in_strength) override {
    return _impl->alwaysOnEnable(in_id, in_effect, in_strength);
  }
  ::ndk::ScopedAStatus alwaysOnDisable(int32_t in_id) override {
    return _impl->alwaysOnDisable(in_id);
  }
  ::ndk::ScopedAStatus getResonantFrequency(float* _aidl_return) override {
    return _impl->getResonantFrequency(_aidl_return);
  }
  ::ndk::ScopedAStatus getQFactor(float* _aidl_return) override {
    return _impl->getQFactor(_aidl_return);
  }
  ::ndk::ScopedAStatus getFrequencyResolution(float* _aidl_return) override {
    return _impl->getFrequencyResolution(_aidl_return);
  }
  ::ndk::ScopedAStatus getFrequencyMinimum(float* _aidl_return) override {
    return _impl->getFrequencyMinimum(_aidl_return);
  }
  ::ndk::ScopedAStatus getBandwidthAmplitudeMap(std::vector<float>* _aidl_return) override {
    return _impl->getBandwidthAmplitudeMap(_aidl_return);
  }
  ::ndk::ScopedAStatus getPwlePrimitiveDurationMax(int32_t* _aidl_return) override {
    return _impl->getPwlePrimitiveDurationMax(_aidl_return);
  }
  ::ndk::ScopedAStatus getPwleCompositionSizeMax(int32_t* _aidl_return) override {
    return _impl->getPwleCompositionSizeMax(_aidl_return);
  }
  ::ndk::ScopedAStatus getSupportedBraking(std::vector<::aidl::android::hardware::vibrator::Braking>* _aidl_return) override {
    return _impl->getSupportedBraking(_aidl_return);
  }
  ::ndk::ScopedAStatus composePwle(const std::vector<::aidl::android::hardware::vibrator::PrimitivePwle>& in_composite, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override {
    return _impl->composePwle(in_composite, in_callback);
  }
protected:
private:
  std::shared_ptr<IVibrator> _impl;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
