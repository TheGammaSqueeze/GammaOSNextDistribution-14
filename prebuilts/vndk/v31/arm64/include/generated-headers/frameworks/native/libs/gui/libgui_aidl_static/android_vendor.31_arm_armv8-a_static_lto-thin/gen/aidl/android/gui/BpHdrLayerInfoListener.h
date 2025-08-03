#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IHdrLayerInfoListener.h>

namespace android {

namespace gui {

class BpHdrLayerInfoListener : public ::android::BpInterface<IHdrLayerInfoListener> {
public:
  explicit BpHdrLayerInfoListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpHdrLayerInfoListener() = default;
  ::android::binder::Status onHdrLayerInfoChanged(int32_t numberOfHdrLayers, int32_t maxW, int32_t maxH, int32_t flags) override;
};  // class BpHdrLayerInfoListener

}  // namespace gui

}  // namespace android
