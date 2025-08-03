#include "aidl/android/hardware/bluetooth/audio/AudioCapabilities.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
const char* AudioCapabilities::descriptor = "android.hardware.bluetooth.audio.AudioCapabilities";

binder_status_t AudioCapabilities::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case pcmCapabilities: {
    ::aidl::android::hardware::bluetooth::audio::PcmCapabilities _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::PcmCapabilities>) {
      set<pcmCapabilities>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<pcmCapabilities>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case a2dpCapabilities: {
    ::aidl::android::hardware::bluetooth::audio::CodecCapabilities _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::CodecCapabilities>) {
      set<a2dpCapabilities>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<a2dpCapabilities>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case leAudioCapabilities: {
    ::aidl::android::hardware::bluetooth::audio::LeAudioCodecCapabilitiesSetting _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::bluetooth::audio::LeAudioCodecCapabilitiesSetting>) {
      set<leAudioCapabilities>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<leAudioCapabilities>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t AudioCapabilities::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case pcmCapabilities: return ::ndk::AParcel_writeData(_parcel, get<pcmCapabilities>());
  case a2dpCapabilities: return ::ndk::AParcel_writeData(_parcel, get<a2dpCapabilities>());
  case leAudioCapabilities: return ::ndk::AParcel_writeData(_parcel, get<leAudioCapabilities>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
