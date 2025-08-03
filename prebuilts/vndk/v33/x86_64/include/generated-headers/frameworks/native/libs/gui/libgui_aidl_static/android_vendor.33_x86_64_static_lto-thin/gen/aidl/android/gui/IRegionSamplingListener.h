#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class IRegionSamplingListener : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(RegionSamplingListener)
  virtual ::android::binder::Status onSampleCollected(float medianLuma) = 0;
};  // class IRegionSamplingListener

class IRegionSamplingListenerDefault : public IRegionSamplingListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onSampleCollected(float /*medianLuma*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IRegionSamplingListenerDefault
}  // namespace gui
}  // namespace android
