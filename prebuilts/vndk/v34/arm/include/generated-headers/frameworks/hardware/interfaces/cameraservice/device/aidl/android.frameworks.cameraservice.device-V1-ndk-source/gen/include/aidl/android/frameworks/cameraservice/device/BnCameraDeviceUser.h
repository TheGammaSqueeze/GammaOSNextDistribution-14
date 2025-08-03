#pragma once

#include "aidl/android/frameworks/cameraservice/device/ICameraDeviceUser.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class BnCameraDeviceUser : public ::ndk::BnCInterface<ICameraDeviceUser> {
public:
  BnCameraDeviceUser();
  virtual ~BnCameraDeviceUser();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICameraDeviceUserDelegator : public BnCameraDeviceUser {
public:
  explicit ICameraDeviceUserDelegator(const std::shared_ptr<ICameraDeviceUser> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICameraDeviceUser::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus beginConfigure() override {
    return _impl->beginConfigure();
  }
  ::ndk::ScopedAStatus cancelRepeatingRequest(int64_t* _aidl_return) override {
    return _impl->cancelRepeatingRequest(_aidl_return);
  }
  ::ndk::ScopedAStatus createDefaultRequest(::aidl::android::frameworks::cameraservice::device::TemplateId in_templateId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) override {
    return _impl->createDefaultRequest(in_templateId, _aidl_return);
  }
  ::ndk::ScopedAStatus createStream(const ::aidl::android::frameworks::cameraservice::device::OutputConfiguration& in_outputConfiguration, int32_t* _aidl_return) override {
    return _impl->createStream(in_outputConfiguration, _aidl_return);
  }
  ::ndk::ScopedAStatus deleteStream(int32_t in_streamId) override {
    return _impl->deleteStream(in_streamId);
  }
  ::ndk::ScopedAStatus disconnect() override {
    return _impl->disconnect();
  }
  ::ndk::ScopedAStatus endConfigure(::aidl::android::frameworks::cameraservice::device::StreamConfigurationMode in_operatingMode, const ::aidl::android::frameworks::cameraservice::device::CameraMetadata& in_sessionParams, int64_t in_startTimeNs) override {
    return _impl->endConfigure(in_operatingMode, in_sessionParams, in_startTimeNs);
  }
  ::ndk::ScopedAStatus flush(int64_t* _aidl_return) override {
    return _impl->flush(_aidl_return);
  }
  ::ndk::ScopedAStatus getCaptureRequestMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override {
    return _impl->getCaptureRequestMetadataQueue(_aidl_return);
  }
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override {
    return _impl->getCaptureResultMetadataQueue(_aidl_return);
  }
  ::ndk::ScopedAStatus isSessionConfigurationSupported(const ::aidl::android::frameworks::cameraservice::device::SessionConfiguration& in_sessionConfiguration, bool* _aidl_return) override {
    return _impl->isSessionConfigurationSupported(in_sessionConfiguration, _aidl_return);
  }
  ::ndk::ScopedAStatus prepare(int32_t in_streamId) override {
    return _impl->prepare(in_streamId);
  }
  ::ndk::ScopedAStatus submitRequestList(const std::vector<::aidl::android::frameworks::cameraservice::device::CaptureRequest>& in_requestList, bool in_isRepeating, ::aidl::android::frameworks::cameraservice::device::SubmitInfo* _aidl_return) override {
    return _impl->submitRequestList(in_requestList, in_isRepeating, _aidl_return);
  }
  ::ndk::ScopedAStatus updateOutputConfiguration(int32_t in_streamId, const ::aidl::android::frameworks::cameraservice::device::OutputConfiguration& in_outputConfiguration) override {
    return _impl->updateOutputConfiguration(in_streamId, in_outputConfiguration);
  }
  ::ndk::ScopedAStatus waitUntilIdle() override {
    return _impl->waitUntilIdle();
  }
protected:
private:
  std::shared_ptr<ICameraDeviceUser> _impl;
};

}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
