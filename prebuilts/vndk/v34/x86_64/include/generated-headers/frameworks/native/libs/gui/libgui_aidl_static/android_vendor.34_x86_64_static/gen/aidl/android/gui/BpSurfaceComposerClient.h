#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/ISurfaceComposerClient.h>

namespace android {
namespace gui {
class BpSurfaceComposerClient : public ::android::BpInterface<ISurfaceComposerClient> {
public:
  explicit BpSurfaceComposerClient(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpSurfaceComposerClient() = default;
  ::android::binder::Status createSurface(const ::std::string& name, int32_t flags, const ::android::sp<::android::IBinder>& parent, const ::android::gui::LayerMetadata& metadata, ::android::gui::CreateSurfaceResult* _aidl_return) override;
  ::android::binder::Status clearLayerFrameStats(const ::android::sp<::android::IBinder>& handle) override;
  ::android::binder::Status getLayerFrameStats(const ::android::sp<::android::IBinder>& handle, ::android::gui::FrameStats* _aidl_return) override;
  ::android::binder::Status mirrorSurface(const ::android::sp<::android::IBinder>& mirrorFromHandle, ::android::gui::CreateSurfaceResult* _aidl_return) override;
  ::android::binder::Status mirrorDisplay(int64_t displayId, ::android::gui::CreateSurfaceResult* _aidl_return) override;
};  // class BpSurfaceComposerClient
}  // namespace gui
}  // namespace android
