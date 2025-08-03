#pragma once

#include "aidl/android/hardware/automotive/occupant_awareness/IOccupantAwareness.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class BnOccupantAwareness : public ::ndk::BnCInterface<IOccupantAwareness> {
public:
  BnOccupantAwareness();
  virtual ~BnOccupantAwareness();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final override;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
