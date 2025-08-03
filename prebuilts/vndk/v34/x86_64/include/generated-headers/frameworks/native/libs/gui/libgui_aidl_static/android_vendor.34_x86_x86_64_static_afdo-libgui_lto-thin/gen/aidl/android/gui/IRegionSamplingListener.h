#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class IRegionSamplingListenerDelegator;

class IRegionSamplingListener : public ::android::IInterface {
public:
  typedef IRegionSamplingListenerDelegator DefaultDelegator;
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
