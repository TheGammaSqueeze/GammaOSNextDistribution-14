#pragma once

#include "aidl/android/hardware/sensors/ISensors.h"

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
namespace sensors {
class BnSensors : public ::ndk::BnCInterface<ISensors> {
public:
  BnSensors();
  virtual ~BnSensors();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISensorsDelegator : public BnSensors {
public:
  explicit ISensorsDelegator(const std::shared_ptr<ISensors> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISensors::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus activate(int32_t in_sensorHandle, bool in_enabled) override {
    return _impl->activate(in_sensorHandle, in_enabled);
  }
  ::ndk::ScopedAStatus batch(int32_t in_sensorHandle, int64_t in_samplingPeriodNs, int64_t in_maxReportLatencyNs) override {
    return _impl->batch(in_sensorHandle, in_samplingPeriodNs, in_maxReportLatencyNs);
  }
  ::ndk::ScopedAStatus configDirectReport(int32_t in_sensorHandle, int32_t in_channelHandle, ::aidl::android::hardware::sensors::ISensors::RateLevel in_rate, int32_t* _aidl_return) override {
    return _impl->configDirectReport(in_sensorHandle, in_channelHandle, in_rate, _aidl_return);
  }
  ::ndk::ScopedAStatus flush(int32_t in_sensorHandle) override {
    return _impl->flush(in_sensorHandle);
  }
  ::ndk::ScopedAStatus getSensorsList(std::vector<::aidl::android::hardware::sensors::SensorInfo>* _aidl_return) override {
    return _impl->getSensorsList(_aidl_return);
  }
  ::ndk::ScopedAStatus initialize(const ::aidl::android::hardware::common::fmq::MQDescriptor<::aidl::android::hardware::sensors::Event, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_eventQueueDescriptor, const ::aidl::android::hardware::common::fmq::MQDescriptor<int32_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_wakeLockDescriptor, const std::shared_ptr<::aidl::android::hardware::sensors::ISensorsCallback>& in_sensorsCallback) override {
    return _impl->initialize(in_eventQueueDescriptor, in_wakeLockDescriptor, in_sensorsCallback);
  }
  ::ndk::ScopedAStatus injectSensorData(const ::aidl::android::hardware::sensors::Event& in_event) override {
    return _impl->injectSensorData(in_event);
  }
  ::ndk::ScopedAStatus registerDirectChannel(const ::aidl::android::hardware::sensors::ISensors::SharedMemInfo& in_mem, int32_t* _aidl_return) override {
    return _impl->registerDirectChannel(in_mem, _aidl_return);
  }
  ::ndk::ScopedAStatus setOperationMode(::aidl::android::hardware::sensors::ISensors::OperationMode in_mode) override {
    return _impl->setOperationMode(in_mode);
  }
  ::ndk::ScopedAStatus unregisterDirectChannel(int32_t in_channelHandle) override {
    return _impl->unregisterDirectChannel(in_channelHandle);
  }
protected:
private:
  std::shared_ptr<ISensors> _impl;
};

}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
