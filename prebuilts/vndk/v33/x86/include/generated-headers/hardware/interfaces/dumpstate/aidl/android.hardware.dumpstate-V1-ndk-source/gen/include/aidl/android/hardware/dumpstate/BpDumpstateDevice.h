#pragma once

#include "aidl/android/hardware/dumpstate/IDumpstateDevice.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace dumpstate {
class BpDumpstateDevice : public ::ndk::BpCInterface<IDumpstateDevice> {
public:
  explicit BpDumpstateDevice(const ::ndk::SpAIBinder& binder);
  virtual ~BpDumpstateDevice();

  ::ndk::ScopedAStatus dumpstateBoard(const std::vector<::ndk::ScopedFileDescriptor>& in_fd, ::aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode in_mode, int64_t in_timeoutMillis) override;
  ::ndk::ScopedAStatus getVerboseLoggingEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus setVerboseLoggingEnabled(bool in_enable) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
}  // namespace aidl
