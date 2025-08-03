#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <utils/StrongPointer.h>

namespace android {

namespace gui {

class IHdrLayerInfoListener : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(HdrLayerInfoListener)
  virtual ::android::binder::Status onHdrLayerInfoChanged(int32_t numberOfHdrLayers, int32_t maxW, int32_t maxH, int32_t flags) = 0;
};  // class IHdrLayerInfoListener

class IHdrLayerInfoListenerDefault : public IHdrLayerInfoListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onHdrLayerInfoChanged(int32_t, int32_t, int32_t, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IHdrLayerInfoListenerDefault

}  // namespace gui

}  // namespace android
