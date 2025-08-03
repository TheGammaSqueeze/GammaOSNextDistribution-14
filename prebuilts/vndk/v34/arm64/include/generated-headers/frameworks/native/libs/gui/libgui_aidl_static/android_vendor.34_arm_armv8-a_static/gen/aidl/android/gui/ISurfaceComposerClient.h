#pragma once

#include <android/gui/CreateSurfaceResult.h>
#include <android/gui/FrameStats.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <gui/LayerMetadata.h>
#include <string>
#include <utils/StrongPointer.h>

namespace android::gui {
class CreateSurfaceResult;
class FrameStats;
}  // namespace android::gui
namespace android {
namespace gui {
class ISurfaceComposerClientDelegator;

class ISurfaceComposerClient : public ::android::IInterface {
public:
  typedef ISurfaceComposerClientDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(SurfaceComposerClient)
  enum : int32_t { eHidden = 4 };
  enum : int32_t { eDestroyBackbuffer = 32 };
  enum : int32_t { eSkipScreenshot = 64 };
  enum : int32_t { eSecure = 128 };
  enum : int32_t { eNonPremultiplied = 256 };
  enum : int32_t { eOpaque = 1024 };
  enum : int32_t { eProtectedByApp = 2048 };
  enum : int32_t { eProtectedByDRM = 4096 };
  enum : int32_t { eCursorWindow = 8192 };
  enum : int32_t { eNoColorFill = 16384 };
  enum : int32_t { eFXSurfaceBufferQueue = 0 };
  enum : int32_t { eFXSurfaceEffect = 131072 };
  enum : int32_t { eFXSurfaceBufferState = 262144 };
  enum : int32_t { eFXSurfaceContainer = 524288 };
  enum : int32_t { eFXSurfaceMask = 983040 };
  virtual ::android::binder::Status createSurface(const ::std::string& name, int32_t flags, const ::android::sp<::android::IBinder>& parent, const ::android::gui::LayerMetadata& metadata, ::android::gui::CreateSurfaceResult* _aidl_return) = 0;
  virtual ::android::binder::Status clearLayerFrameStats(const ::android::sp<::android::IBinder>& handle) = 0;
  virtual ::android::binder::Status getLayerFrameStats(const ::android::sp<::android::IBinder>& handle, ::android::gui::FrameStats* _aidl_return) = 0;
  virtual ::android::binder::Status mirrorSurface(const ::android::sp<::android::IBinder>& mirrorFromHandle, ::android::gui::CreateSurfaceResult* _aidl_return) = 0;
  virtual ::android::binder::Status mirrorDisplay(int64_t displayId, ::android::gui::CreateSurfaceResult* _aidl_return) = 0;
};  // class ISurfaceComposerClient

class ISurfaceComposerClientDefault : public ISurfaceComposerClient {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status createSurface(const ::std::string& /*name*/, int32_t /*flags*/, const ::android::sp<::android::IBinder>& /*parent*/, const ::android::gui::LayerMetadata& /*metadata*/, ::android::gui::CreateSurfaceResult* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status clearLayerFrameStats(const ::android::sp<::android::IBinder>& /*handle*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getLayerFrameStats(const ::android::sp<::android::IBinder>& /*handle*/, ::android::gui::FrameStats* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status mirrorSurface(const ::android::sp<::android::IBinder>& /*mirrorFromHandle*/, ::android::gui::CreateSurfaceResult* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status mirrorDisplay(int64_t /*displayId*/, ::android::gui::CreateSurfaceResult* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ISurfaceComposerClientDefault
}  // namespace gui
}  // namespace android
