#pragma once

#include "aidl/android/hardware/bluetooth/audio/IBluetoothAudioPort.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class BpBluetoothAudioPort : public ::ndk::BpCInterface<IBluetoothAudioPort> {
public:
  explicit BpBluetoothAudioPort(const ::ndk::SpAIBinder& binder);
  virtual ~BpBluetoothAudioPort();

  ::ndk::ScopedAStatus getPresentationPosition(::aidl::android::hardware::bluetooth::audio::PresentationPosition* _aidl_return) override;
  ::ndk::ScopedAStatus startStream(bool in_isLowLatency) override;
  ::ndk::ScopedAStatus stopStream() override;
  ::ndk::ScopedAStatus suspendStream() override;
  ::ndk::ScopedAStatus updateSourceMetadata(const ::aidl::android::hardware::audio::common::SourceMetadata& in_sourceMetadata) override;
  ::ndk::ScopedAStatus updateSinkMetadata(const ::aidl::android::hardware::audio::common::SinkMetadata& in_sinkMetadata) override;
  ::ndk::ScopedAStatus setLatencyMode(::aidl::android::hardware::bluetooth::audio::LatencyMode in_latencyMode) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
