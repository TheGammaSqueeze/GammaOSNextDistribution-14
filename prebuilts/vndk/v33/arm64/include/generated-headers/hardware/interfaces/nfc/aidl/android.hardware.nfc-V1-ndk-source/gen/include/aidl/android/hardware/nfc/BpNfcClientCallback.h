#pragma once

#include "aidl/android/hardware/nfc/INfcClientCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
class BpNfcClientCallback : public ::ndk::BpCInterface<INfcClientCallback> {
public:
  explicit BpNfcClientCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpNfcClientCallback();

  ::ndk::ScopedAStatus sendData(const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus sendEvent(::aidl::android::hardware::nfc::NfcEvent in_event, ::aidl::android::hardware::nfc::NfcStatus in_status) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
