#pragma once

#include <android/binder_interface_utils.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/vibrator/Braking.h>
#include <aidl/android/hardware/vibrator/CompositeEffect.h>
#include <aidl/android/hardware/vibrator/CompositePrimitive.h>
#include <aidl/android/hardware/vibrator/Effect.h>
#include <aidl/android/hardware/vibrator/EffectStrength.h>
#include <aidl/android/hardware/vibrator/IVibratorCallback.h>
#include <aidl/android/hardware/vibrator/PrimitivePwle.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class IVibrator : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IVibrator();
  virtual ~IVibrator();

  enum : int32_t { CAP_ON_CALLBACK = 1 };
  enum : int32_t { CAP_PERFORM_CALLBACK = 2 };
  enum : int32_t { CAP_AMPLITUDE_CONTROL = 4 };
  enum : int32_t { CAP_EXTERNAL_CONTROL = 8 };
  enum : int32_t { CAP_EXTERNAL_AMPLITUDE_CONTROL = 16 };
  enum : int32_t { CAP_COMPOSE_EFFECTS = 32 };
  enum : int32_t { CAP_ALWAYS_ON_CONTROL = 64 };
  enum : int32_t { CAP_GET_RESONANT_FREQUENCY = 128 };
  enum : int32_t { CAP_GET_Q_FACTOR = 256 };
  enum : int32_t { CAP_FREQUENCY_CONTROL = 512 };
  enum : int32_t { CAP_COMPOSE_PWLE_EFFECTS = 1024 };
  static const int32_t version = 2;
  static inline const std::string hash = "ea8742d6993e1a82917da38b9938e537aa7fcb54";
  static constexpr uint32_t TRANSACTION_getCapabilities = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_off = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_on = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_perform = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getSupportedEffects = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_setAmplitude = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_setExternalControl = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getCompositionDelayMax = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getCompositionSizeMax = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getSupportedPrimitives = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getPrimitiveDuration = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_compose = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getSupportedAlwaysOnEffects = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_alwaysOnEnable = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_alwaysOnDisable = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getResonantFrequency = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getQFactor = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_getFrequencyResolution = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_getFrequencyMinimum = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_getBandwidthAmplitudeMap = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_getPwlePrimitiveDurationMax = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_getPwleCompositionSizeMax = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_getSupportedBraking = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_composePwle = FIRST_CALL_TRANSACTION + 23;

  static std::shared_ptr<IVibrator> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IVibrator>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IVibrator>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IVibrator>& impl);
  static const std::shared_ptr<IVibrator>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus off() = 0;
  virtual ::ndk::ScopedAStatus on(int32_t in_timeoutMs, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus perform(::aidl::android::hardware::vibrator::Effect in_effect, ::aidl::android::hardware::vibrator::EffectStrength in_strength, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSupportedEffects(std::vector<::aidl::android::hardware::vibrator::Effect>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setAmplitude(float in_amplitude) = 0;
  virtual ::ndk::ScopedAStatus setExternalControl(bool in_enabled) = 0;
  virtual ::ndk::ScopedAStatus getCompositionDelayMax(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCompositionSizeMax(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSupportedPrimitives(std::vector<::aidl::android::hardware::vibrator::CompositePrimitive>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPrimitiveDuration(::aidl::android::hardware::vibrator::CompositePrimitive in_primitive, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus compose(const std::vector<::aidl::android::hardware::vibrator::CompositeEffect>& in_composite, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getSupportedAlwaysOnEffects(std::vector<::aidl::android::hardware::vibrator::Effect>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus alwaysOnEnable(int32_t in_id, ::aidl::android::hardware::vibrator::Effect in_effect, ::aidl::android::hardware::vibrator::EffectStrength in_strength) = 0;
  virtual ::ndk::ScopedAStatus alwaysOnDisable(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus getResonantFrequency(float* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getQFactor(float* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getFrequencyResolution(float* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getFrequencyMinimum(float* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getBandwidthAmplitudeMap(std::vector<float>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPwlePrimitiveDurationMax(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPwleCompositionSizeMax(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSupportedBraking(std::vector<::aidl::android::hardware::vibrator::Braking>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus composePwle(const std::vector<::aidl::android::hardware::vibrator::PrimitivePwle>& in_composite, const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IVibrator> default_impl;
};
class IVibratorDefault : public IVibrator {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
