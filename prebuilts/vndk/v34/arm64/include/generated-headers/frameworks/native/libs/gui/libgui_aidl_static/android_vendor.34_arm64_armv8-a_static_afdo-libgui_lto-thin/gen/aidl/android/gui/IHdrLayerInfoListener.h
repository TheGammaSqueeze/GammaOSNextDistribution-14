#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class IHdrLayerInfoListenerDelegator;

class IHdrLayerInfoListener : public ::android::IInterface {
public:
  typedef IHdrLayerInfoListenerDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(HdrLayerInfoListener)
  virtual ::android::binder::Status onHdrLayerInfoChanged(int32_t numberOfHdrLayers, int32_t maxW, int32_t maxH, int32_t flags, float maxDesiredHdrSdrRatio) = 0;
};  // class IHdrLayerInfoListener

class IHdrLayerInfoListenerDefault : public IHdrLayerInfoListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onHdrLayerInfoChanged(int32_t /*numberOfHdrLayers*/, int32_t /*maxW*/, int32_t /*maxH*/, int32_t /*flags*/, float /*maxDesiredHdrSdrRatio*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IHdrLayerInfoListenerDefault
}  // namespace gui
}  // namespace android
