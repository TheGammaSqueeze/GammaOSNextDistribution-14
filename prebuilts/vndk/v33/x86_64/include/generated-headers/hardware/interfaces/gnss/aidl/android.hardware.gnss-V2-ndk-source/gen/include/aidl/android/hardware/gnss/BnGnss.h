#pragma once

#include "aidl/android/hardware/gnss/IGnss.h"

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
namespace gnss {
class BnGnss : public ::ndk::BnCInterface<IGnss> {
public:
  BnGnss();
  virtual ~BnGnss();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssDelegator : public BnGnss {
public:
  explicit IGnssDelegator(const std::shared_ptr<IGnss> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnss::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus close() override {
    return _impl->close();
  }
  ::ndk::ScopedAStatus getExtensionPsds(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsds>* _aidl_return) override {
    return _impl->getExtensionPsds(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssConfiguration(std::shared_ptr<::aidl::android::hardware::gnss::IGnssConfiguration>* _aidl_return) override {
    return _impl->getExtensionGnssConfiguration(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssMeasurement(std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementInterface>* _aidl_return) override {
    return _impl->getExtensionGnssMeasurement(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssPowerIndication(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPowerIndication>* _aidl_return) override {
    return _impl->getExtensionGnssPowerIndication(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssBatching(std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatching>* _aidl_return) override {
    return _impl->getExtensionGnssBatching(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssGeofence(std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofence>* _aidl_return) override {
    return _impl->getExtensionGnssGeofence(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssNavigationMessage(std::shared_ptr<::aidl::android::hardware::gnss::IGnssNavigationMessageInterface>* _aidl_return) override {
    return _impl->getExtensionGnssNavigationMessage(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionAGnss(std::shared_ptr<::aidl::android::hardware::gnss::IAGnss>* _aidl_return) override {
    return _impl->getExtensionAGnss(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionAGnssRil(std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRil>* _aidl_return) override {
    return _impl->getExtensionAGnssRil(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssDebug(std::shared_ptr<::aidl::android::hardware::gnss::IGnssDebug>* _aidl_return) override {
    return _impl->getExtensionGnssDebug(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionGnssVisibilityControl(std::shared_ptr<::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControl>* _aidl_return) override {
    return _impl->getExtensionGnssVisibilityControl(_aidl_return);
  }
  ::ndk::ScopedAStatus start() override {
    return _impl->start();
  }
  ::ndk::ScopedAStatus stop() override {
    return _impl->stop();
  }
  ::ndk::ScopedAStatus injectTime(int64_t in_timeMs, int64_t in_timeReferenceMs, int32_t in_uncertaintyMs) override {
    return _impl->injectTime(in_timeMs, in_timeReferenceMs, in_uncertaintyMs);
  }
  ::ndk::ScopedAStatus injectLocation(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override {
    return _impl->injectLocation(in_location);
  }
  ::ndk::ScopedAStatus injectBestLocation(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override {
    return _impl->injectBestLocation(in_location);
  }
  ::ndk::ScopedAStatus deleteAidingData(::aidl::android::hardware::gnss::IGnss::GnssAidingData in_aidingDataFlags) override {
    return _impl->deleteAidingData(in_aidingDataFlags);
  }
  ::ndk::ScopedAStatus setPositionMode(const ::aidl::android::hardware::gnss::IGnss::PositionModeOptions& in_options) override {
    return _impl->setPositionMode(in_options);
  }
  ::ndk::ScopedAStatus getExtensionGnssAntennaInfo(std::shared_ptr<::aidl::android::hardware::gnss::IGnssAntennaInfo>* _aidl_return) override {
    return _impl->getExtensionGnssAntennaInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus getExtensionMeasurementCorrections(std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsInterface>* _aidl_return) override {
    return _impl->getExtensionMeasurementCorrections(_aidl_return);
  }
  ::ndk::ScopedAStatus startSvStatus() override {
    return _impl->startSvStatus();
  }
  ::ndk::ScopedAStatus stopSvStatus() override {
    return _impl->stopSvStatus();
  }
  ::ndk::ScopedAStatus startNmea() override {
    return _impl->startNmea();
  }
  ::ndk::ScopedAStatus stopNmea() override {
    return _impl->stopNmea();
  }
protected:
private:
  std::shared_ptr<IGnss> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
