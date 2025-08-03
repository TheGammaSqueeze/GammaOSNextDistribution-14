#pragma once

#include <binder/IInterface.h>
#include <android/gui/ISurfaceComposerClient.h>
#include <android/gui/BnSurfaceComposerClient.h>
#include <binder/Delegate.h>


namespace android {
namespace gui {
class BnSurfaceComposerClient : public ::android::BnInterface<ISurfaceComposerClient> {
public:
  static constexpr uint32_t TRANSACTION_createSurface = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_clearLayerFrameStats = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getLayerFrameStats = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_mirrorSurface = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_mirrorDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  explicit BnSurfaceComposerClient();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnSurfaceComposerClient

class ISurfaceComposerClientDelegator : public BnSurfaceComposerClient {
public:
  explicit ISurfaceComposerClientDelegator(const ::android::sp<ISurfaceComposerClient> &impl) : _aidl_delegate(impl) {}

  ::android::sp<ISurfaceComposerClient> getImpl() { return _aidl_delegate; }
  ::android::binder::Status createSurface(const ::std::string& name, int32_t flags, const ::android::sp<::android::IBinder>& parent, const ::android::gui::LayerMetadata& metadata, ::android::gui::CreateSurfaceResult* _aidl_return) override {
    return _aidl_delegate->createSurface(name, flags, parent, metadata, _aidl_return);
  }
  ::android::binder::Status clearLayerFrameStats(const ::android::sp<::android::IBinder>& handle) override {
    return _aidl_delegate->clearLayerFrameStats(handle);
  }
  ::android::binder::Status getLayerFrameStats(const ::android::sp<::android::IBinder>& handle, ::android::gui::FrameStats* _aidl_return) override {
    return _aidl_delegate->getLayerFrameStats(handle, _aidl_return);
  }
  ::android::binder::Status mirrorSurface(const ::android::sp<::android::IBinder>& mirrorFromHandle, ::android::gui::CreateSurfaceResult* _aidl_return) override {
    return _aidl_delegate->mirrorSurface(mirrorFromHandle, _aidl_return);
  }
  ::android::binder::Status mirrorDisplay(int64_t displayId, ::android::gui::CreateSurfaceResult* _aidl_return) override {
    return _aidl_delegate->mirrorDisplay(displayId, _aidl_return);
  }
private:
  ::android::sp<ISurfaceComposerClient> _aidl_delegate;
};  // class ISurfaceComposerClientDelegator
}  // namespace gui
}  // namespace android
