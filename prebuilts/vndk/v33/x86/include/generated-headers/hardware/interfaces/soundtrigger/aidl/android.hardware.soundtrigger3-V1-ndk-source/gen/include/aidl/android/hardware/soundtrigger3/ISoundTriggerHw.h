#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/soundtrigger3/ISoundTriggerHwCallback.h>
#include <aidl/android/hardware/soundtrigger3/ISoundTriggerHwGlobalCallback.h>
#include <aidl/android/media/soundtrigger/ModelParameter.h>
#include <aidl/android/media/soundtrigger/ModelParameterRange.h>
#include <aidl/android/media/soundtrigger/PhraseSoundModel.h>
#include <aidl/android/media/soundtrigger/Properties.h>
#include <aidl/android/media/soundtrigger/RecognitionConfig.h>
#include <aidl/android/media/soundtrigger/SoundModel.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace soundtrigger3 {
class ISoundTriggerHw : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISoundTriggerHw();
  virtual ~ISoundTriggerHw();

  static const int32_t version = 1;
  static inline const std::string hash = "7d8d63478cd50e766d2072140c8aa3457f9fb585";
  static constexpr uint32_t TRANSACTION_getProperties = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_registerGlobalCallback = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_loadSoundModel = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_loadPhraseSoundModel = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_unloadSoundModel = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_startRecognition = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_stopRecognition = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_forceRecognitionEvent = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_queryParameter = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getParameter = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_setParameter = FIRST_CALL_TRANSACTION + 10;

  static std::shared_ptr<ISoundTriggerHw> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISoundTriggerHw>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISoundTriggerHw>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISoundTriggerHw>& impl);
  static const std::shared_ptr<ISoundTriggerHw>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getProperties(::aidl::android::media::soundtrigger::Properties* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus registerGlobalCallback(const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwGlobalCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus loadSoundModel(const ::aidl::android::media::soundtrigger::SoundModel& in_soundModel, const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwCallback>& in_callback, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus loadPhraseSoundModel(const ::aidl::android::media::soundtrigger::PhraseSoundModel& in_soundModel, const std::shared_ptr<::aidl::android::hardware::soundtrigger3::ISoundTriggerHwCallback>& in_callback, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus unloadSoundModel(int32_t in_modelHandle) = 0;
  virtual ::ndk::ScopedAStatus startRecognition(int32_t in_modelHandle, int32_t in_deviceHandle, int32_t in_ioHandle, const ::aidl::android::media::soundtrigger::RecognitionConfig& in_config) = 0;
  virtual ::ndk::ScopedAStatus stopRecognition(int32_t in_modelHandle) = 0;
  virtual ::ndk::ScopedAStatus forceRecognitionEvent(int32_t in_modelHandle) = 0;
  virtual ::ndk::ScopedAStatus queryParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, std::optional<::aidl::android::media::soundtrigger::ModelParameterRange>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setParameter(int32_t in_modelHandle, ::aidl::android::media::soundtrigger::ModelParameter in_modelParam, int32_t in_value) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISoundTriggerHw> default_impl;
};
class ISoundTriggerHwDefault : public ISoundTriggerHw {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
