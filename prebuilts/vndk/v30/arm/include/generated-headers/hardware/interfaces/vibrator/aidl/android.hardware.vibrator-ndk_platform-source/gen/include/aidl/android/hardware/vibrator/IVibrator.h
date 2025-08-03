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
#include <aidl/android/hardware/vibrator/CompositeEffect.h>
#include <aidl/android/hardware/vibrator/CompositePrimitive.h>
#include <aidl/android/hardware/vibrator/Effect.h>
#include <aidl/android/hardware/vibrator/EffectStrength.h>
#include <aidl/android/hardware/vibrator/IVibratorCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class IVibrator : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IVibrator();
  virtual ~IVibrator();


  enum : int32_t {
    CAP_ON_CALLBACK = 1,
    CAP_PERFORM_CALLBACK = 2,
    CAP_AMPLITUDE_CONTROL = 4,
    CAP_EXTERNAL_CONTROL = 8,
    CAP_EXTERNAL_AMPLITUDE_CONTROL = 16,
    CAP_COMPOSE_EFFECTS = 32,
    CAP_ALWAYS_ON_CONTROL = 64,
  };
  static const int32_t version = 1;
  static inline const std::string hash = "eeab78b6096b029f424ab5ce9c2c4ef1249a5cb0";

  static std::shared_ptr<IVibrator> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IVibrator>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IVibrator>* instance);
  static bool setDefaultImpl(std::shared_ptr<IVibrator> impl);
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
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
