#pragma once

#include "aidl/android/hardware/camera/device/ICameraDeviceSession.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class BnCameraDeviceSession : public ::ndk::BnCInterface<ICameraDeviceSession> {
public:
  BnCameraDeviceSession();
  virtual ~BnCameraDeviceSession();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraDeviceSessionDelegator : public BnCameraDeviceSession {
public:
  explicit ICameraDeviceSessionDelegator(const std::shared_ptr<ICameraDeviceSession> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraDeviceSession::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
  ::ndk::ScopedAStatus configureStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, std::vector<::aidl::android::hardware::camera::device::HalStream>* _aidl_return) override {
    return _impl->configureStreams(in_requestedConfiguration, _aidl_return);
  }
  ::ndk::ScopedAStatus constructDefaultRequestSettings(::aidl::android::hardware::camera::device::RequestTemplate in_type, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override {
    return _impl->constructDefaultRequestSettings(in_type, _aidl_return);
  }
  ::ndk::ScopedAStatus flush() override {
    return _impl->flush();
  }
  ::ndk::ScopedAStatus getCaptureRequestMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override {
    return _impl->getCaptureRequestMetadataQueue(_aidl_return);
  }
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override {
    return _impl->getCaptureResultMetadataQueue(_aidl_return);
  }
  ::ndk::ScopedAStatus isReconfigurationRequired(const ::aidl::android::hardware::camera::device::CameraMetadata& in_oldSessionParams, const ::aidl::android::hardware::camera::device::CameraMetadata& in_newSessionParams, bool* _aidl_return) override {
    return _impl->isReconfigurationRequired(in_oldSessionParams, in_newSessionParams, _aidl_return);
  }
  ::ndk::ScopedAStatus processCaptureRequest(const std::vector<::aidl::android::hardware::camera::device::CaptureRequest>& in_requests, const std::vector<::aidl::android::hardware::camera::device::BufferCache>& in_cachesToRemove, int32_t* _aidl_return) override {
    return _impl->processCaptureRequest(in_requests, in_cachesToRemove, _aidl_return);
  }
  ::ndk::ScopedAStatus signalStreamFlush(const std::vector<int32_t>& in_streamIds, int32_t in_streamConfigCounter) override {
    return _impl->signalStreamFlush(in_streamIds, in_streamConfigCounter);
  }
  ::ndk::ScopedAStatus switchToOffline(const std::vector<int32_t>& in_streamsToKeep, ::aidl::android::hardware::camera::device::CameraOfflineSessionInfo* out_offlineSessionInfo, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraOfflineSession>* _aidl_return) override {
    return _impl->switchToOffline(in_streamsToKeep, out_offlineSessionInfo, _aidl_return);
  }
  ::ndk::ScopedAStatus repeatingRequestEnd(int32_t in_frameNumber, const std::vector<int32_t>& in_streamIds) override {
    return _impl->repeatingRequestEnd(in_frameNumber, in_streamIds);
  }
protected:
private:
  std::shared_ptr<ICameraDeviceSession> _impl;
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
