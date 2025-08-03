#pragma once

#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHw.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace soundtrigger3 {
class BpSoundTriggerHw : public ::ndk::BpCInterface<ISoundTriggerHw> {
public:
  explicit BpSoundTriggerHw(const ::ndk::SpAIBinder& binder);
  virtual ~BpSoundTriggerHw();

  ::ndk::ScopedAStatus getProperties(::aidl::android::media::soundtrigger::Properties* _aidl_return) override;
  ::ndk::ScopedAStatus registerGlobalCallback(const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwGlobalCallback>& in_callback) override;
  ::ndk::ScopedAStatus loadSoundModel(const ::aidl::android::media::soundtrigger::SoundModel& in_soundModel, const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwCallback>& in_callback, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus loadPhraseSoundModel(const ::aidl::android::media::soundtrigger::PhraseSoundModel& in_soundModel, const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwCallback>& in_callback, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus unloadSoundModel(int32_t in_modelHandle) override;
  ::ndk::ScopedAStatus startRecognition(int32_t in_modelHandle, int32_t in_deviceHandle, int32_t in_ioHandle, const ::aidl::android::media::soundtrigger::RecognitionConfig& in_config) override;
  ::ndk::ScopedAStatus stopRecognition(int32_t in_modelHandle) override;
  ::ndk::ScopedAStatus forceRecognitionEvent(int32_t in_modelHandle) override;
  ::ndk::ScopedAStatus queryParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, std::optional<::aidl::android::media::soundtrigger::ModelParameterRange>* _aidl_return) override;
  ::ndk::ScopedAStatus getParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus setParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, int32_t in_value) override;
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
