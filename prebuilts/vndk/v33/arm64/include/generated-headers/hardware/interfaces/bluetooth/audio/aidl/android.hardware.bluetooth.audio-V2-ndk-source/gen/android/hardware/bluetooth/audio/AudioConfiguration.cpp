#include "aidl/android/hardware/bluetooth/audio/AudioConfiguration.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
const char* AudioConfiguration::descriptor = "android.hardware.bluetooth.audio.AudioConfiguration";

binder_status_t AudioConfiguration::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case pcmConfig: {
    ::aidl::android::hardware::bluetooth::audio::PcmConfiguration _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::PcmConfiguration>) {
      set<pcmConfig>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<pcmConfig>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case a2dpConfig: {
    ::aidl::android::hardware::bluetooth::audio::CodecConfiguration _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::CodecConfiguration>) {
      set<a2dpConfig>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<a2dpConfig>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case leAudioConfig: {
    ::aidl::android::hardware::bluetooth::audio::LeAudioConfiguration _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::LeAudioConfiguration>) {
      set<leAudioConfig>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<leAudioConfig>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case leAudioBroadcastConfig: {
    ::aidl::android::hardware::bluetooth::audio::LeAudioBroadcastConfiguration _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::LeAudioBroadcastConfiguration>) {
      set<leAudioBroadcastConfig>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<leAudioBroadcastConfig>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t AudioConfiguration::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case pcmConfig: return ::ndk::AParcel_writeData(_parcel, get<pcmConfig>());
  case a2dpConfig: return ::ndk::AParcel_writeData(_parcel, get<a2dpConfig>());
  case leAudioConfig: return ::ndk::AParcel_writeData(_parcel, get<leAudioConfig>());
  case leAudioBroadcastConfig: return ::ndk::AParcel_writeData(_parcel, get<leAudioBroadcastConfig>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
