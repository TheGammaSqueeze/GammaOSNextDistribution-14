#pragma once

#include "aidl/android/hardware/health/storage/IGarbageCollectCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
namespace storage {
class BnGarbageCollectCallback : public ::ndk::BnCInterface<IGarbageCollectCallback> {
public:
  BnGarbageCollectCallback();
  virtual ~BnGarbageCollectCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
