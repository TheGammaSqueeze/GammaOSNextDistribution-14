#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/health/BatteryCapacityLevel.h>
#include <aidl/android/hardware/health/BatteryHealth.h>
#include <aidl/android/hardware/health/BatteryStatus.h>
#include <aidl/android/hardware/health/DiskStats.h>
#include <aidl/android/hardware/health/StorageInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace health {
class HealthInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool chargerAcOnline = false;
  bool chargerUsbOnline = false;
  bool chargerWirelessOnline = false;
  bool chargerDockOnline = false;
  int32_t maxChargingCurrentMicroamps = 0;
  int32_t maxChargingVoltageMicrovolts = 0;
  ::aidl::android::hardware::health::BatteryStatus batteryStatus = ::aidl::android::hardware::health::BatteryStatus(0);
  ::aidl::android::hardware::health::BatteryHealth batteryHealth = ::aidl::android::hardware::health::BatteryHealth(0);
  bool batteryPresent = false;
  int32_t batteryLevel = 0;
  int32_t batteryVoltageMillivolts = 0;
  int32_t batteryTemperatureTenthsCelsius = 0;
  int32_t batteryCurrentMicroamps = 0;
  int32_t batteryCycleCount = 0;
  int32_t batteryFullChargeUah = 0;
  int32_t batteryChargeCounterUah = 0;
  std::string batteryTechnology;
  int32_t batteryCurrentAverageMicroamps = 0;
  std::vector<::aidl::android::hardware::health::DiskStats> diskStats;
  std::vector<::aidl::android::hardware::health::StorageInfo> storageInfos;
  ::aidl::android::hardware::health::BatteryCapacityLevel batteryCapacityLevel = ::aidl::android::hardware::health::BatteryCapacityLevel(0);
  int64_t batteryChargeTimeToFullNowSeconds = 0L;
  int32_t batteryFullChargeDesignCapacityUah = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HealthInfo& rhs) const {
    return std::tie(chargerAcOnline, chargerUsbOnline, chargerWirelessOnline, chargerDockOnline, maxChargingCurrentMicroamps, maxChargingVoltageMicrovolts, batteryStatus, batteryHealth, batteryPresent, batteryLevel, batteryVoltageMillivolts, batteryTemperatureTenthsCelsius, batteryCurrentMicroamps, batteryCycleCount, batteryFullChargeUah, batteryChargeCounterUah, batteryTechnology, batteryCurrentAverageMicroamps, diskStats, storageInfos, batteryCapacityLevel, batteryChargeTimeToFullNowSeconds, batteryFullChargeDesignCapacityUah) != std::tie(rhs.chargerAcOnline, rhs.chargerUsbOnline, rhs.chargerWirelessOnline, rhs.chargerDockOnline, rhs.maxChargingCurrentMicroamps, rhs.maxChargingVoltageMicrovolts, rhs.batteryStatus, rhs.batteryHealth, rhs.batteryPresent, rhs.batteryLevel, rhs.batteryVoltageMillivolts, rhs.batteryTemperatureTenthsCelsius, rhs.batteryCurrentMicroamps, rhs.batteryCycleCount, rhs.batteryFullChargeUah, rhs.batteryChargeCounterUah, rhs.batteryTechnology, rhs.batteryCurrentAverageMicroamps, rhs.diskStats, rhs.storageInfos, rhs.batteryCapacityLevel, rhs.batteryChargeTimeToFullNowSeconds, rhs.batteryFullChargeDesignCapacityUah);
  }
  inline bool operator<(const HealthInfo& rhs) const {
    return std::tie(chargerAcOnline, chargerUsbOnline, chargerWirelessOnline, chargerDockOnline, maxChargingCurrentMicroamps, maxChargingVoltageMicrovolts, batteryStatus, batteryHealth, batteryPresent, batteryLevel, batteryVoltageMillivolts, batteryTemperatureTenthsCelsius, batteryCurrentMicroamps, batteryCycleCount, batteryFullChargeUah, batteryChargeCounterUah, batteryTechnology, batteryCurrentAverageMicroamps, diskStats, storageInfos, batteryCapacityLevel, batteryChargeTimeToFullNowSeconds, batteryFullChargeDesignCapacityUah) < std::tie(rhs.chargerAcOnline, rhs.chargerUsbOnline, rhs.chargerWirelessOnline, rhs.chargerDockOnline, rhs.maxChargingCurrentMicroamps, rhs.maxChargingVoltageMicrovolts, rhs.batteryStatus, rhs.batteryHealth, rhs.batteryPresent, rhs.batteryLevel, rhs.batteryVoltageMillivolts, rhs.batteryTemperatureTenthsCelsius, rhs.batteryCurrentMicroamps, rhs.batteryCycleCount, rhs.batteryFullChargeUah, rhs.batteryChargeCounterUah, rhs.batteryTechnology, rhs.batteryCurrentAverageMicroamps, rhs.diskStats, rhs.storageInfos, rhs.batteryCapacityLevel, rhs.batteryChargeTimeToFullNowSeconds, rhs.batteryFullChargeDesignCapacityUah);
  }
  inline bool operator<=(const HealthInfo& rhs) const {
    return std::tie(chargerAcOnline, chargerUsbOnline, chargerWirelessOnline, chargerDockOnline, maxChargingCurrentMicroamps, maxChargingVoltageMicrovolts, batteryStatus, batteryHealth, batteryPresent, batteryLevel, batteryVoltageMillivolts, batteryTemperatureTenthsCelsius, batteryCurrentMicroamps, batteryCycleCount, batteryFullChargeUah, batteryChargeCounterUah, batteryTechnology, batteryCurrentAverageMicroamps, diskStats, storageInfos, batteryCapacityLevel, batteryChargeTimeToFullNowSeconds, batteryFullChargeDesignCapacityUah) <= std::tie(rhs.chargerAcOnline, rhs.chargerUsbOnline, rhs.chargerWirelessOnline, rhs.chargerDockOnline, rhs.maxChargingCurrentMicroamps, rhs.maxChargingVoltageMicrovolts, rhs.batteryStatus, rhs.batteryHealth, rhs.batteryPresent, rhs.batteryLevel, rhs.batteryVoltageMillivolts, rhs.batteryTemperatureTenthsCelsius, rhs.batteryCurrentMicroamps, rhs.batteryCycleCount, rhs.batteryFullChargeUah, rhs.batteryChargeCounterUah, rhs.batteryTechnology, rhs.batteryCurrentAverageMicroamps, rhs.diskStats, rhs.storageInfos, rhs.batteryCapacityLevel, rhs.batteryChargeTimeToFullNowSeconds, rhs.batteryFullChargeDesignCapacityUah);
  }
  inline bool operator==(const HealthInfo& rhs) const {
    return std::tie(chargerAcOnline, chargerUsbOnline, chargerWirelessOnline, chargerDockOnline, maxChargingCurrentMicroamps, maxChargingVoltageMicrovolts, batteryStatus, batteryHealth, batteryPresent, batteryLevel, batteryVoltageMillivolts, batteryTemperatureTenthsCelsius, batteryCurrentMicroamps, batteryCycleCount, batteryFullChargeUah, batteryChargeCounterUah, batteryTechnology, batteryCurrentAverageMicroamps, diskStats, storageInfos, batteryCapacityLevel, batteryChargeTimeToFullNowSeconds, batteryFullChargeDesignCapacityUah) == std::tie(rhs.chargerAcOnline, rhs.chargerUsbOnline, rhs.chargerWirelessOnline, rhs.chargerDockOnline, rhs.maxChargingCurrentMicroamps, rhs.maxChargingVoltageMicrovolts, rhs.batteryStatus, rhs.batteryHealth, rhs.batteryPresent, rhs.batteryLevel, rhs.batteryVoltageMillivolts, rhs.batteryTemperatureTenthsCelsius, rhs.batteryCurrentMicroamps, rhs.batteryCycleCount, rhs.batteryFullChargeUah, rhs.batteryChargeCounterUah, rhs.batteryTechnology, rhs.batteryCurrentAverageMicroamps, rhs.diskStats, rhs.storageInfos, rhs.batteryCapacityLevel, rhs.batteryChargeTimeToFullNowSeconds, rhs.batteryFullChargeDesignCapacityUah);
  }
  inline bool operator>(const HealthInfo& rhs) const {
    return std::tie(chargerAcOnline, chargerUsbOnline, chargerWirelessOnline, chargerDockOnline, maxChargingCurrentMicroamps, maxChargingVoltageMicrovolts, batteryStatus, batteryHealth, batteryPresent, batteryLevel, batteryVoltageMillivolts, batteryTemperatureTenthsCelsius, batteryCurrentMicroamps, batteryCycleCount, batteryFullChargeUah, batteryChargeCounterUah, batteryTechnology, batteryCurrentAverageMicroamps, diskStats, storageInfos, batteryCapacityLevel, batteryChargeTimeToFullNowSeconds, batteryFullChargeDesignCapacityUah) > std::tie(rhs.chargerAcOnline, rhs.chargerUsbOnline, rhs.chargerWirelessOnline, rhs.chargerDockOnline, rhs.maxChargingCurrentMicroamps, rhs.maxChargingVoltageMicrovolts, rhs.batteryStatus, rhs.batteryHealth, rhs.batteryPresent, rhs.batteryLevel, rhs.batteryVoltageMillivolts, rhs.batteryTemperatureTenthsCelsius, rhs.batteryCurrentMicroamps, rhs.batteryCycleCount, rhs.batteryFullChargeUah, rhs.batteryChargeCounterUah, rhs.batteryTechnology, rhs.batteryCurrentAverageMicroamps, rhs.diskStats, rhs.storageInfos, rhs.batteryCapacityLevel, rhs.batteryChargeTimeToFullNowSeconds, rhs.batteryFullChargeDesignCapacityUah);
  }
  inline bool operator>=(const HealthInfo& rhs) const {
    return std::tie(chargerAcOnline, chargerUsbOnline, chargerWirelessOnline, chargerDockOnline, maxChargingCurrentMicroamps, maxChargingVoltageMicrovolts, batteryStatus, batteryHealth, batteryPresent, batteryLevel, batteryVoltageMillivolts, batteryTemperatureTenthsCelsius, batteryCurrentMicroamps, batteryCycleCount, batteryFullChargeUah, batteryChargeCounterUah, batteryTechnology, batteryCurrentAverageMicroamps, diskStats, storageInfos, batteryCapacityLevel, batteryChargeTimeToFullNowSeconds, batteryFullChargeDesignCapacityUah) >= std::tie(rhs.chargerAcOnline, rhs.chargerUsbOnline, rhs.chargerWirelessOnline, rhs.chargerDockOnline, rhs.maxChargingCurrentMicroamps, rhs.maxChargingVoltageMicrovolts, rhs.batteryStatus, rhs.batteryHealth, rhs.batteryPresent, rhs.batteryLevel, rhs.batteryVoltageMillivolts, rhs.batteryTemperatureTenthsCelsius, rhs.batteryCurrentMicroamps, rhs.batteryCycleCount, rhs.batteryFullChargeUah, rhs.batteryChargeCounterUah, rhs.batteryTechnology, rhs.batteryCurrentAverageMicroamps, rhs.diskStats, rhs.storageInfos, rhs.batteryCapacityLevel, rhs.batteryChargeTimeToFullNowSeconds, rhs.batteryFullChargeDesignCapacityUah);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { BATTERY_CHARGE_TIME_TO_FULL_NOW_SECONDS_UNSUPPORTED = -1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "HealthInfo{";
    os << "chargerAcOnline: " << ::android::internal::ToString(chargerAcOnline);
    os << ", chargerUsbOnline: " << ::android::internal::ToString(chargerUsbOnline);
    os << ", chargerWirelessOnline: " << ::android::internal::ToString(chargerWirelessOnline);
    os << ", chargerDockOnline: " << ::android::internal::ToString(chargerDockOnline);
    os << ", maxChargingCurrentMicroamps: " << ::android::internal::ToString(maxChargingCurrentMicroamps);
    os << ", maxChargingVoltageMicrovolts: " << ::android::internal::ToString(maxChargingVoltageMicrovolts);
    os << ", batteryStatus: " << ::android::internal::ToString(batteryStatus);
    os << ", batteryHealth: " << ::android::internal::ToString(batteryHealth);
    os << ", batteryPresent: " << ::android::internal::ToString(batteryPresent);
    os << ", batteryLevel: " << ::android::internal::ToString(batteryLevel);
    os << ", batteryVoltageMillivolts: " << ::android::internal::ToString(batteryVoltageMillivolts);
    os << ", batteryTemperatureTenthsCelsius: " << ::android::internal::ToString(batteryTemperatureTenthsCelsius);
    os << ", batteryCurrentMicroamps: " << ::android::internal::ToString(batteryCurrentMicroamps);
    os << ", batteryCycleCount: " << ::android::internal::ToString(batteryCycleCount);
    os << ", batteryFullChargeUah: " << ::android::internal::ToString(batteryFullChargeUah);
    os << ", batteryChargeCounterUah: " << ::android::internal::ToString(batteryChargeCounterUah);
    os << ", batteryTechnology: " << ::android::internal::ToString(batteryTechnology);
    os << ", batteryCurrentAverageMicroamps: " << ::android::internal::ToString(batteryCurrentAverageMicroamps);
    os << ", diskStats: " << ::android::internal::ToString(diskStats);
    os << ", storageInfos: " << ::android::internal::ToString(storageInfos);
    os << ", batteryCapacityLevel: " << ::android::internal::ToString(batteryCapacityLevel);
    os << ", batteryChargeTimeToFullNowSeconds: " << ::android::internal::ToString(batteryChargeTimeToFullNowSeconds);
    os << ", batteryFullChargeDesignCapacityUah: " << ::android::internal::ToString(batteryFullChargeDesignCapacityUah);
    os << "}";
    return os.str();
  }
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
