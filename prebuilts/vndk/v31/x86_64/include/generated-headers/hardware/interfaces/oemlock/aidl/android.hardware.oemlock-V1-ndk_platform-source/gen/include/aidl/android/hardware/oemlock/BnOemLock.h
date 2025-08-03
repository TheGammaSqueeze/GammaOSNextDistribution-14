#pragma once

#include "aidl/android/hardware/oemlock/IOemLock.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace oemlock {
class BnOemLock : public ::ndk::BnCInterface<IOemLock> {
public:
  BnOemLock();
  virtual ~BnOemLock();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace oemlock
}  // namespace hardware
}  // namespace android
}  // namespace aidl
