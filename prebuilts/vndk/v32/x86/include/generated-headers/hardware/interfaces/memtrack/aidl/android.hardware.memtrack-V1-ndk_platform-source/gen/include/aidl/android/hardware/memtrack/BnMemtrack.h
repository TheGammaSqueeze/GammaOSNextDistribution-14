#pragma once

#include "aidl/android/hardware/memtrack/IMemtrack.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace memtrack {
class BnMemtrack : public ::ndk::BnCInterface<IMemtrack> {
public:
  BnMemtrack();
  virtual ~BnMemtrack();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace memtrack
}  // namespace hardware
}  // namespace android
}  // namespace aidl
