#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IRegionSamplingListener.h>

namespace android {
namespace gui {
class BpRegionSamplingListener : public ::android::BpInterface<IRegionSamplingListener> {
public:
  explicit BpRegionSamplingListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpRegionSamplingListener() = default;
  ::android::binder::Status onSampleCollected(float medianLuma) override;
};  // class BpRegionSamplingListener
}  // namespace gui
}  // namespace android
