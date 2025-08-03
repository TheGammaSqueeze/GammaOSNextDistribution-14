#pragma once

#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHwCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace soundtrigger3 {
class BpSoundTriggerHwCallback : public ::ndk::BpCInterface<ISoundTriggerHwCallback> {
public:
  explicit BpSoundTriggerHwCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSoundTriggerHwCallback();

  ::ndk::ScopedAStatus modelUnloaded(int32_t in_model) override;
  ::ndk::ScopedAStatus phraseRecognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::PhraseRecognitionEvent& in_event) override;
  ::ndk::ScopedAStatus recognitionCallback(int32_t in_model, const ::aidl::android::media::soundtrigger::RecognitionEvent& in_event) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
