#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/GnssLocation.h>
#include <aidl/android/hardware/gnss/IAGnss.h>
#include <aidl/android/hardware/gnss/IAGnssRil.h>
#include <aidl/android/hardware/gnss/IGnss.h>
#include <aidl/android/hardware/gnss/IGnssAntennaInfo.h>
#include <aidl/android/hardware/gnss/IGnssBatching.h>
#include <aidl/android/hardware/gnss/IGnssCallback.h>
#include <aidl/android/hardware/gnss/IGnssConfiguration.h>
#include <aidl/android/hardware/gnss/IGnssDebug.h>
#include <aidl/android/hardware/gnss/IGnssGeofence.h>
#include <aidl/android/hardware/gnss/IGnssMeasurementInterface.h>
#include <aidl/android/hardware/gnss/IGnssNavigationMessageInterface.h>
#include <aidl/android/hardware/gnss/IGnssPowerIndication.h>
#include <aidl/android/hardware/gnss/IGnssPsds.h>
#include <aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsInterface.h>
#include <aidl/android/hardware/gnss/visibility_control/IGnssVisibilityControl.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnss : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnss();
  virtual ~IGnss();

  enum class GnssPositionMode : int32_t {
    STANDALONE = 0,
    MS_BASED = 1,
    MS_ASSISTED = 2,
  };

  enum class GnssPositionRecurrence : int32_t {
    RECURRENCE_PERIODIC = 0,
    RECURRENCE_SINGLE = 1,
  };

  enum class GnssAidingData : int32_t {
    EPHEMERIS = 1,
    ALMANAC = 2,
    POSITION = 4,
    TIME = 8,
    IONO = 16,
    UTC = 32,
    HEALTH = 64,
    SVDIR = 128,
    SVSTEER = 256,
    SADATA = 512,
    RTI = 1024,
    CELLDB_INFO = 32768,
    ALL = 65535,
  };

  class PositionModeOptions {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::aidl::android::hardware::gnss::IGnss::GnssPositionMode mode = ::aidl::android::hardware::gnss::IGnss::GnssPositionMode(0);
    ::aidl::android::hardware::gnss::IGnss::GnssPositionRecurrence recurrence = ::aidl::android::hardware::gnss::IGnss::GnssPositionRecurrence(0);
    int32_t minIntervalMs = 0;
    int32_t preferredAccuracyMeters = 0;
    int32_t preferredTimeMs = 0;
    bool lowPowerMode = false;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const PositionModeOptions& rhs) const {
      return std::tie(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs, lowPowerMode) != std::tie(rhs.mode, rhs.recurrence, rhs.minIntervalMs, rhs.preferredAccuracyMeters, rhs.preferredTimeMs, rhs.lowPowerMode);
    }
    inline bool operator<(const PositionModeOptions& rhs) const {
      return std::tie(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs, lowPowerMode) < std::tie(rhs.mode, rhs.recurrence, rhs.minIntervalMs, rhs.preferredAccuracyMeters, rhs.preferredTimeMs, rhs.lowPowerMode);
    }
    inline bool operator<=(const PositionModeOptions& rhs) const {
      return std::tie(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs, lowPowerMode) <= std::tie(rhs.mode, rhs.recurrence, rhs.minIntervalMs, rhs.preferredAccuracyMeters, rhs.preferredTimeMs, rhs.lowPowerMode);
    }
    inline bool operator==(const PositionModeOptions& rhs) const {
      return std::tie(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs, lowPowerMode) == std::tie(rhs.mode, rhs.recurrence, rhs.minIntervalMs, rhs.preferredAccuracyMeters, rhs.preferredTimeMs, rhs.lowPowerMode);
    }
    inline bool operator>(const PositionModeOptions& rhs) const {
      return std::tie(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs, lowPowerMode) > std::tie(rhs.mode, rhs.recurrence, rhs.minIntervalMs, rhs.preferredAccuracyMeters, rhs.preferredTimeMs, rhs.lowPowerMode);
    }
    inline bool operator>=(const PositionModeOptions& rhs) const {
      return std::tie(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs, lowPowerMode) >= std::tie(rhs.mode, rhs.recurrence, rhs.minIntervalMs, rhs.preferredAccuracyMeters, rhs.preferredTimeMs, rhs.lowPowerMode);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "PositionModeOptions{";
      os << "mode: " << ::android::internal::ToString(mode);
      os << ", recurrence: " << ::android::internal::ToString(recurrence);
      os << ", minIntervalMs: " << ::android::internal::ToString(minIntervalMs);
      os << ", preferredAccuracyMeters: " << ::android::internal::ToString(preferredAccuracyMeters);
      os << ", preferredTimeMs: " << ::android::internal::ToString(preferredTimeMs);
      os << ", lowPowerMode: " << ::android::internal::ToString(lowPowerMode);
      os << "}";
      return os.str();
    }
  };
  enum : int32_t { ERROR_INVALID_ARGUMENT = 1 };
  enum : int32_t { ERROR_ALREADY_INIT = 2 };
  enum : int32_t { ERROR_GENERIC = 3 };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getExtensionPsds = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getExtensionGnssConfiguration = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getExtensionGnssMeasurement = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getExtensionGnssPowerIndication = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getExtensionGnssBatching = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getExtensionGnssGeofence = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getExtensionGnssNavigationMessage = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getExtensionAGnss = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getExtensionAGnssRil = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getExtensionGnssDebug = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getExtensionGnssVisibilityControl = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_start = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_stop = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_injectTime = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_injectLocation = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_injectBestLocation = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_deleteAidingData = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_setPositionMode = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_getExtensionGnssAntennaInfo = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_getExtensionMeasurementCorrections = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_startSvStatus = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_stopSvStatus = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_startNmea = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_stopNmea = FIRST_CALL_TRANSACTION + 25;

  static std::shared_ptr<IGnss> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnss>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnss>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnss>& impl);
  static const std::shared_ptr<IGnss>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus getExtensionPsds(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsds>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssConfiguration(std::shared_ptr<::aidl::android::hardware::gnss::IGnssConfiguration>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssMeasurement(std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementInterface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssPowerIndication(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPowerIndication>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssBatching(std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatching>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssGeofence(std::shared_ptr<::aidl::android::hardware::gnss::IGnssGeofence>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssNavigationMessage(std::shared_ptr<::aidl::android::hardware::gnss::IGnssNavigationMessageInterface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionAGnss(std::shared_ptr<::aidl::android::hardware::gnss::IAGnss>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionAGnssRil(std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRil>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssDebug(std::shared_ptr<::aidl::android::hardware::gnss::IGnssDebug>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssVisibilityControl(std::shared_ptr<::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControl>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus start() = 0;
  virtual ::ndk::ScopedAStatus stop() = 0;
  virtual ::ndk::ScopedAStatus injectTime(int64_t in_timeMs, int64_t in_timeReferenceMs, int32_t in_uncertaintyMs) = 0;
  virtual ::ndk::ScopedAStatus injectLocation(const ::aidl::android::hardware::gnss::GnssLocation& in_location) = 0;
  virtual ::ndk::ScopedAStatus injectBestLocation(const ::aidl::android::hardware::gnss::GnssLocation& in_location) = 0;
  virtual ::ndk::ScopedAStatus deleteAidingData(::aidl::android::hardware::gnss::IGnss::GnssAidingData in_aidingDataFlags) = 0;
  virtual ::ndk::ScopedAStatus setPositionMode(const ::aidl::android::hardware::gnss::IGnss::PositionModeOptions& in_options) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssAntennaInfo(std::shared_ptr<::aidl::android::hardware::gnss::IGnssAntennaInfo>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionMeasurementCorrections(std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsInterface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus startSvStatus() = 0;
  virtual ::ndk::ScopedAStatus stopSvStatus() = 0;
  virtual ::ndk::ScopedAStatus startNmea() = 0;
  virtual ::ndk::ScopedAStatus stopNmea() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnss> default_impl;
};
class IGnssDefault : public IGnss {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnss::GnssPositionMode val) {
  switch(val) {
  case IGnss::GnssPositionMode::STANDALONE:
    return "STANDALONE";
  case IGnss::GnssPositionMode::MS_BASED:
    return "MS_BASED";
  case IGnss::GnssPositionMode::MS_ASSISTED:
    return "MS_ASSISTED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IGnss::GnssPositionMode, 3> enum_values<aidl::android::hardware::gnss::IGnss::GnssPositionMode> = {
  aidl::android::hardware::gnss::IGnss::GnssPositionMode::STANDALONE,
  aidl::android::hardware::gnss::IGnss::GnssPositionMode::MS_BASED,
  aidl::android::hardware::gnss::IGnss::GnssPositionMode::MS_ASSISTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnss::GnssPositionRecurrence val) {
  switch(val) {
  case IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC:
    return "RECURRENCE_PERIODIC";
  case IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE:
    return "RECURRENCE_SINGLE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IGnss::GnssPositionRecurrence, 2> enum_values<aidl::android::hardware::gnss::IGnss::GnssPositionRecurrence> = {
  aidl::android::hardware::gnss::IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC,
  aidl::android::hardware::gnss::IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnss::GnssAidingData val) {
  switch(val) {
  case IGnss::GnssAidingData::EPHEMERIS:
    return "EPHEMERIS";
  case IGnss::GnssAidingData::ALMANAC:
    return "ALMANAC";
  case IGnss::GnssAidingData::POSITION:
    return "POSITION";
  case IGnss::GnssAidingData::TIME:
    return "TIME";
  case IGnss::GnssAidingData::IONO:
    return "IONO";
  case IGnss::GnssAidingData::UTC:
    return "UTC";
  case IGnss::GnssAidingData::HEALTH:
    return "HEALTH";
  case IGnss::GnssAidingData::SVDIR:
    return "SVDIR";
  case IGnss::GnssAidingData::SVSTEER:
    return "SVSTEER";
  case IGnss::GnssAidingData::SADATA:
    return "SADATA";
  case IGnss::GnssAidingData::RTI:
    return "RTI";
  case IGnss::GnssAidingData::CELLDB_INFO:
    return "CELLDB_INFO";
  case IGnss::GnssAidingData::ALL:
    return "ALL";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IGnss::GnssAidingData, 13> enum_values<aidl::android::hardware::gnss::IGnss::GnssAidingData> = {
  aidl::android::hardware::gnss::IGnss::GnssAidingData::EPHEMERIS,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::ALMANAC,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::POSITION,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::TIME,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::IONO,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::UTC,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::HEALTH,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::SVDIR,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::SVSTEER,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::SADATA,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::RTI,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::CELLDB_INFO,
  aidl::android::hardware::gnss::IGnss::GnssAidingData::ALL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
