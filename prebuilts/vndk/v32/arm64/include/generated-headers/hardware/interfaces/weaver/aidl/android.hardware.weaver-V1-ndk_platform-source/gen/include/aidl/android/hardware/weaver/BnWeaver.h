#pragma once

#include "aidl/android/hardware/weaver/IWeaver.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace weaver {
class BnWeaver : public ::ndk::BnCInterface<IWeaver> {
public:
  BnWeaver();
  virtual ~BnWeaver();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace weaver
}  // namespace hardware
}  // namespace android
}  // namespace aidl
