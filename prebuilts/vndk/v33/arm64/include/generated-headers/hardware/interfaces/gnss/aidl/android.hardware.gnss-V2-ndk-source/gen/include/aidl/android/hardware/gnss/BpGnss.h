#pragma once

#include "aidl/android/hardware/gnss/IGnss.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnss : public ::ndk::BpCInterface<IGnss> {
public:
  explicit BpGnss(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnss();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssCallback>& in_callback) override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus getExtensionPsds(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsds>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssConfiguration(std::shared_ptr<::aidl::android::hardware::gnss::IGnssConfiguration>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssMeasurement(std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementInterface>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssPowerIndication(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPowerIndication>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssBatching(std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatching>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssGeofence(std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofence>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssNavigationMessage(std::shared_ptr<::aidl::android::hardware::gnss::IGnssNavigationMessageInterface>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionAGnss(std::shared_ptr<::aidl::android::hardware::gnss::IAGnss>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionAGnssRil(std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRil>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssDebug(std::shared_ptr<::aidl::android::hardware::gnss::IGnssDebug>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssVisibilityControl(std::shared_ptr<::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControl>* _aidl_return) override;
  ::ndk::ScopedAStatus start() override;
  ::ndk::ScopedAStatus stop() override;
  ::ndk::ScopedAStatus injectTime(int64_t in_timeMs, int64_t in_timeReferenceMs, int32_t in_uncertaintyMs) override;
  ::ndk::ScopedAStatus injectLocation(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override;
  ::ndk::ScopedAStatus injectBestLocation(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override;
  ::ndk::ScopedAStatus deleteAidingData(::aidl::android::hardware::gnss::IGnss::GnssAidingData in_aidingDataFlags) override;
  ::ndk::ScopedAStatus setPositionMode(const ::aidl::android::hardware::gnss::IGnss::PositionModeOptions& in_options) override;
  ::ndk::ScopedAStatus getExtensionGnssAntennaInfo(std::shared_ptr<::aidl::android::hardware::gnss::IGnssAntennaInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionMeasurementCorrections(std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsInterface>* _aidl_return) override;
  ::ndk::ScopedAStatus startSvStatus() override;
  ::ndk::ScopedAStatus stopSvStatus() override;
  ::ndk::ScopedAStatus startNmea() override;
  ::ndk::ScopedAStatus stopNmea() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
