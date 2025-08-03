#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/media/soundtrigger/PhraseRecognitionEvent.h>
#include <aidl/android/media/soundtrigger/RecognitionEvent.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace soundtrigger3 {
class ISoundTriggerHwCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISoundTriggerHwCallback();
  virtual ~ISoundTriggerHwCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "7d8d63478cd50e766d2072140c8aa3457f9fb585";
  static constexpr uint32_t TRANSACTION_modelUnloaded = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_phraseRecognitionCallback = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_recognitionCallback = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<ISoundTriggerHwCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISoundTriggerHwCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISoundTriggerHwCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISoundTriggerHwCallback>& impl);
  static const std::shared_ptr<ISoundTriggerHwCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus modelUnloaded(int32_t in_model) = 0;
  virtual ::ndk::ScopedAStatus phraseRecognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::PhraseRecognitionEvent& in_event) = 0;
  virtual ::ndk::ScopedAStatus recognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::RecognitionEvent& in_event) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISoundTriggerHwCallback> default_impl;
};
class ISoundTriggerHwCallbackDefault : public ISoundTriggerHwCallback {
public:
  ::ndk::ScopedAStatus modelUnloaded(int32_t in_model) override;
  ::ndk::ScopedAStatus phraseRecognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::PhraseRecognitionEvent& in_event) override;
  ::ndk::ScopedAStatus recognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::RecognitionEvent& in_event) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
