#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/usb/ContaminantDetectionStatus.h>
#include <aidl/android/hardware/usb/ContaminantProtectionMode.h>
#include <aidl/android/hardware/usb/ContaminantProtectionStatus.h>
#include <aidl/android/hardware/usb/PortDataRole.h>
#include <aidl/android/hardware/usb/PortMode.h>
#include <aidl/android/hardware/usb/PortPowerRole.h>
#include <aidl/android/hardware/usb/PowerBrickStatus.h>
#include <aidl/android/hardware/usb/UsbDataStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace usb {
class PortStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string portName;
  ::aidl::android::hardware::usb::PortDataRole currentDataRole = ::aidl::android::hardware::usb::PortDataRole::NONE;
  ::aidl::android::hardware::usb::PortPowerRole currentPowerRole = ::aidl::android::hardware::usb::PortPowerRole::NONE;
  ::aidl::android::hardware::usb::PortMode currentMode = ::aidl::android::hardware::usb::PortMode::NONE;
  bool canChangeMode = false;
  bool canChangeDataRole = false;
  bool canChangePowerRole = false;
  std::vector<::aidl::android::hardware::usb::PortMode> supportedModes;
  std::vector<::aidl::android::hardware::usb::ContaminantProtectionMode> supportedContaminantProtectionModes;
  bool supportsEnableContaminantPresenceProtection = false;
  ::aidl::android::hardware::usb::ContaminantProtectionStatus contaminantProtectionStatus = ::aidl::android::hardware::usb::ContaminantProtectionStatus::NONE;
  bool supportsEnableContaminantPresenceDetection = false;
  ::aidl::android::hardware::usb::ContaminantDetectionStatus contaminantDetectionStatus = ::aidl::android::hardware::usb::ContaminantDetectionStatus::NOT_SUPPORTED;
  std::vector<::aidl::android::hardware::usb::UsbDataStatus> usbDataStatus;
  bool powerTransferLimited = false;
  ::aidl::android::hardware::usb::PowerBrickStatus powerBrickStatus = ::aidl::android::hardware::usb::PowerBrickStatus(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PortStatus& rhs) const {
    return std::tie(portName, currentDataRole, currentPowerRole, currentMode, canChangeMode, canChangeDataRole, canChangePowerRole, supportedModes, supportedContaminantProtectionModes, supportsEnableContaminantPresenceProtection, contaminantProtectionStatus, supportsEnableContaminantPresenceDetection, contaminantDetectionStatus, usbDataStatus, powerTransferLimited, powerBrickStatus) != std::tie(rhs.portName, rhs.currentDataRole, rhs.currentPowerRole, rhs.currentMode, rhs.canChangeMode, rhs.canChangeDataRole, rhs.canChangePowerRole, rhs.supportedModes, rhs.supportedContaminantProtectionModes, rhs.supportsEnableContaminantPresenceProtection, rhs.contaminantProtectionStatus, rhs.supportsEnableContaminantPresenceDetection, rhs.contaminantDetectionStatus, rhs.usbDataStatus, rhs.powerTransferLimited, rhs.powerBrickStatus);
  }
  inline bool operator<(const PortStatus& rhs) const {
    return std::tie(portName, currentDataRole, currentPowerRole, currentMode, canChangeMode, canChangeDataRole, canChangePowerRole, supportedModes, supportedContaminantProtectionModes, supportsEnableContaminantPresenceProtection, contaminantProtectionStatus, supportsEnableContaminantPresenceDetection, contaminantDetectionStatus, usbDataStatus, powerTransferLimited, powerBrickStatus) < std::tie(rhs.portName, rhs.currentDataRole, rhs.currentPowerRole, rhs.currentMode, rhs.canChangeMode, rhs.canChangeDataRole, rhs.canChangePowerRole, rhs.supportedModes, rhs.supportedContaminantProtectionModes, rhs.supportsEnableContaminantPresenceProtection, rhs.contaminantProtectionStatus, rhs.supportsEnableContaminantPresenceDetection, rhs.contaminantDetectionStatus, rhs.usbDataStatus, rhs.powerTransferLimited, rhs.powerBrickStatus);
  }
  inline bool operator<=(const PortStatus& rhs) const {
    return std::tie(portName, currentDataRole, currentPowerRole, currentMode, canChangeMode, canChangeDataRole, canChangePowerRole, supportedModes, supportedContaminantProtectionModes, supportsEnableContaminantPresenceProtection, contaminantProtectionStatus, supportsEnableContaminantPresenceDetection, contaminantDetectionStatus, usbDataStatus, powerTransferLimited, powerBrickStatus) <= std::tie(rhs.portName, rhs.currentDataRole, rhs.currentPowerRole, rhs.currentMode, rhs.canChangeMode, rhs.canChangeDataRole, rhs.canChangePowerRole, rhs.supportedModes, rhs.supportedContaminantProtectionModes, rhs.supportsEnableContaminantPresenceProtection, rhs.contaminantProtectionStatus, rhs.supportsEnableContaminantPresenceDetection, rhs.contaminantDetectionStatus, rhs.usbDataStatus, rhs.powerTransferLimited, rhs.powerBrickStatus);
  }
  inline bool operator==(const PortStatus& rhs) const {
    return std::tie(portName, currentDataRole, currentPowerRole, currentMode, canChangeMode, canChangeDataRole, canChangePowerRole, supportedModes, supportedContaminantProtectionModes, supportsEnableContaminantPresenceProtection, contaminantProtectionStatus, supportsEnableContaminantPresenceDetection, contaminantDetectionStatus, usbDataStatus, powerTransferLimited, powerBrickStatus) == std::tie(rhs.portName, rhs.currentDataRole, rhs.currentPowerRole, rhs.currentMode, rhs.canChangeMode, rhs.canChangeDataRole, rhs.canChangePowerRole, rhs.supportedModes, rhs.supportedContaminantProtectionModes, rhs.supportsEnableContaminantPresenceProtection, rhs.contaminantProtectionStatus, rhs.supportsEnableContaminantPresenceDetection, rhs.contaminantDetectionStatus, rhs.usbDataStatus, rhs.powerTransferLimited, rhs.powerBrickStatus);
  }
  inline bool operator>(const PortStatus& rhs) const {
    return std::tie(portName, currentDataRole, currentPowerRole, currentMode, canChangeMode, canChangeDataRole, canChangePowerRole, supportedModes, supportedContaminantProtectionModes, supportsEnableContaminantPresenceProtection, contaminantProtectionStatus, supportsEnableContaminantPresenceDetection, contaminantDetectionStatus, usbDataStatus, powerTransferLimited, powerBrickStatus) > std::tie(rhs.portName, rhs.currentDataRole, rhs.currentPowerRole, rhs.currentMode, rhs.canChangeMode, rhs.canChangeDataRole, rhs.canChangePowerRole, rhs.supportedModes, rhs.supportedContaminantProtectionModes, rhs.supportsEnableContaminantPresenceProtection, rhs.contaminantProtectionStatus, rhs.supportsEnableContaminantPresenceDetection, rhs.contaminantDetectionStatus, rhs.usbDataStatus, rhs.powerTransferLimited, rhs.powerBrickStatus);
  }
  inline bool operator>=(const PortStatus& rhs) const {
    return std::tie(portName, currentDataRole, currentPowerRole, currentMode, canChangeMode, canChangeDataRole, canChangePowerRole, supportedModes, supportedContaminantProtectionModes, supportsEnableContaminantPresenceProtection, contaminantProtectionStatus, supportsEnableContaminantPresenceDetection, contaminantDetectionStatus, usbDataStatus, powerTransferLimited, powerBrickStatus) >= std::tie(rhs.portName, rhs.currentDataRole, rhs.currentPowerRole, rhs.currentMode, rhs.canChangeMode, rhs.canChangeDataRole, rhs.canChangePowerRole, rhs.supportedModes, rhs.supportedContaminantProtectionModes, rhs.supportsEnableContaminantPresenceProtection, rhs.contaminantProtectionStatus, rhs.supportsEnableContaminantPresenceDetection, rhs.contaminantDetectionStatus, rhs.usbDataStatus, rhs.powerTransferLimited, rhs.powerBrickStatus);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PortStatus{";
    os << "portName: " << ::android::internal::ToString(portName);
    os << ", currentDataRole: " << ::android::internal::ToString(currentDataRole);
    os << ", currentPowerRole: " << ::android::internal::ToString(currentPowerRole);
    os << ", currentMode: " << ::android::internal::ToString(currentMode);
    os << ", canChangeMode: " << ::android::internal::ToString(canChangeMode);
    os << ", canChangeDataRole: " << ::android::internal::ToString(canChangeDataRole);
    os << ", canChangePowerRole: " << ::android::internal::ToString(canChangePowerRole);
    os << ", supportedModes: " << ::android::internal::ToString(supportedModes);
    os << ", supportedContaminantProtectionModes: " << ::android::internal::ToString(supportedContaminantProtectionModes);
    os << ", supportsEnableContaminantPresenceProtection: " << ::android::internal::ToString(supportsEnableContaminantPresenceProtection);
    os << ", contaminantProtectionStatus: " << ::android::internal::ToString(contaminantProtectionStatus);
    os << ", supportsEnableContaminantPresenceDetection: " << ::android::internal::ToString(supportsEnableContaminantPresenceDetection);
    os << ", contaminantDetectionStatus: " << ::android::internal::ToString(contaminantDetectionStatus);
    os << ", usbDataStatus: " << ::android::internal::ToString(usbDataStatus);
    os << ", powerTransferLimited: " << ::android::internal::ToString(powerTransferLimited);
    os << ", powerBrickStatus: " << ::android::internal::ToString(powerBrickStatus);
    os << "}";
    return os.str();
  }
};
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
