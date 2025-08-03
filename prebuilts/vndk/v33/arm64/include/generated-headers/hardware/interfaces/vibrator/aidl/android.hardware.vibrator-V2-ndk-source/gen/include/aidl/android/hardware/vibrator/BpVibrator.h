#pragma once

#include "aidl/android/hardware/vibrator/IVibrator.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BpVibrator : public ::ndk::BpCInterface<IVibrator> {
public:
  explicit BpVibrator(const ::ndk::SpAIBinder& binder);
  virtual ~BpVibrator();

  ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus off() override;
  ::ndk::ScopedAStatus on(int32_t in_timeoutMs, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override;
  ::ndk::ScopedAStatus perform(::aidl::android::hardware::vibrator::Effect in_effect, ::aidl::android::hardware::vibrator::EffectStrength in_strength, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getSupportedEffects(std::vector<::aidl::android::hardware::vibrator::Effect>* _aidl_return) override;
  ::ndk::ScopedAStatus setAmplitude(float in_amplitude) override;
  ::ndk::ScopedAStatus setExternalControl(bool in_enabled) override;
  ::ndk::ScopedAStatus getCompositionDelayMax(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getCompositionSizeMax(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getSupportedPrimitives(std::vector<::aidl::android::hardware::vibrator::CompositePrimitive>* _aidl_return) override;
  ::ndk::ScopedAStatus getPrimitiveDuration(::aidl::android::hardware::vibrator::CompositePrimitive in_primitive, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus compose(const std::vector<::aidl::android::hardware::vibrator::CompositeEffect>& in_composite, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override;
  ::ndk::ScopedAStatus getSupportedAlwaysOnEffects(std::vector<::aidl::android::hardware::vibrator::Effect>* _aidl_return) override;
  ::ndk::ScopedAStatus alwaysOnEnable(int32_t in_id, ::aidl::android::hardware::vibrator::Effect in_effect, ::aidl::android::hardware::vibrator::EffectStrength in_strength) override;
  ::ndk::ScopedAStatus alwaysOnDisable(int32_t in_id) override;
  ::ndk::ScopedAStatus getResonantFrequency(float* _aidl_return) override;
  ::ndk::ScopedAStatus getQFactor(float* _aidl_return) override;
  ::ndk::ScopedAStatus getFrequencyResolution(float* _aidl_return) override;
  ::ndk::ScopedAStatus getFrequencyMinimum(float* _aidl_return) override;
  ::ndk::ScopedAStatus getBandwidthAmplitudeMap(std::vector<float>* _aidl_return) override;
  ::ndk::ScopedAStatus getPwlePrimitiveDurationMax(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getPwleCompositionSizeMax(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getSupportedBraking(std::vector<::aidl::android::hardware::vibrator::Braking>* _aidl_return) override;
  ::ndk::ScopedAStatus composePwle(const std::vector<::aidl::android::hardware::vibrator::PrimitivePwle>& in_composite, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
