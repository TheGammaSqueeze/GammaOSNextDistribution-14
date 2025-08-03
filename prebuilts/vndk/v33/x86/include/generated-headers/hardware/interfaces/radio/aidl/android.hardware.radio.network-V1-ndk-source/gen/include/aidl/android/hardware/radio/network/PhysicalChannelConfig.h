#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/RadioTechnology.h>
#include <aidl/android/hardware/radio/network/CellConnectionStatus.h>
#include <aidl/android/hardware/radio/network/PhysicalChannelConfigBand.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class PhysicalChannelConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::CellConnectionStatus status = ::aidl::android::hardware::radio::network::CellConnectionStatus(0);
  ::aidl::android::hardware::radio::RadioTechnology rat = ::aidl::android::hardware::radio::RadioTechnology(0);
  int32_t downlinkChannelNumber = 0;
  int32_t uplinkChannelNumber = 0;
  int32_t cellBandwidthDownlinkKhz = 0;
  int32_t cellBandwidthUplinkKhz = 0;
  std::vector<int32_t> contextIds;
  int32_t physicalCellId = 0;
  ::aidl::android::hardware::radio::network::PhysicalChannelConfigBand band;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhysicalChannelConfig& rhs) const {
    return std::tie(status, rat, downlinkChannelNumber, uplinkChannelNumber, cellBandwidthDownlinkKhz, cellBandwidthUplinkKhz, contextIds, physicalCellId, band) != std::tie(rhs.status, rhs.rat, rhs.downlinkChannelNumber, rhs.uplinkChannelNumber, rhs.cellBandwidthDownlinkKhz, rhs.cellBandwidthUplinkKhz, rhs.contextIds, rhs.physicalCellId, rhs.band);
  }
  inline bool operator<(const PhysicalChannelConfig& rhs) const {
    return std::tie(status, rat, downlinkChannelNumber, uplinkChannelNumber, cellBandwidthDownlinkKhz, cellBandwidthUplinkKhz, contextIds, physicalCellId, band) < std::tie(rhs.status, rhs.rat, rhs.downlinkChannelNumber, rhs.uplinkChannelNumber, rhs.cellBandwidthDownlinkKhz, rhs.cellBandwidthUplinkKhz, rhs.contextIds, rhs.physicalCellId, rhs.band);
  }
  inline bool operator<=(const PhysicalChannelConfig& rhs) const {
    return std::tie(status, rat, downlinkChannelNumber, uplinkChannelNumber, cellBandwidthDownlinkKhz, cellBandwidthUplinkKhz, contextIds, physicalCellId, band) <= std::tie(rhs.status, rhs.rat, rhs.downlinkChannelNumber, rhs.uplinkChannelNumber, rhs.cellBandwidthDownlinkKhz, rhs.cellBandwidthUplinkKhz, rhs.contextIds, rhs.physicalCellId, rhs.band);
  }
  inline bool operator==(const PhysicalChannelConfig& rhs) const {
    return std::tie(status, rat, downlinkChannelNumber, uplinkChannelNumber, cellBandwidthDownlinkKhz, cellBandwidthUplinkKhz, contextIds, physicalCellId, band) == std::tie(rhs.status, rhs.rat, rhs.downlinkChannelNumber, rhs.uplinkChannelNumber, rhs.cellBandwidthDownlinkKhz, rhs.cellBandwidthUplinkKhz, rhs.contextIds, rhs.physicalCellId, rhs.band);
  }
  inline bool operator>(const PhysicalChannelConfig& rhs) const {
    return std::tie(status, rat, downlinkChannelNumber, uplinkChannelNumber, cellBandwidthDownlinkKhz, cellBandwidthUplinkKhz, contextIds, physicalCellId, band) > std::tie(rhs.status, rhs.rat, rhs.downlinkChannelNumber, rhs.uplinkChannelNumber, rhs.cellBandwidthDownlinkKhz, rhs.cellBandwidthUplinkKhz, rhs.contextIds, rhs.physicalCellId, rhs.band);
  }
  inline bool operator>=(const PhysicalChannelConfig& rhs) const {
    return std::tie(status, rat, downlinkChannelNumber, uplinkChannelNumber, cellBandwidthDownlinkKhz, cellBandwidthUplinkKhz, contextIds, physicalCellId, band) >= std::tie(rhs.status, rhs.rat, rhs.downlinkChannelNumber, rhs.uplinkChannelNumber, rhs.cellBandwidthDownlinkKhz, rhs.cellBandwidthUplinkKhz, rhs.contextIds, rhs.physicalCellId, rhs.band);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhysicalChannelConfig{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", rat: " << ::android::internal::ToString(rat);
    os << ", downlinkChannelNumber: " << ::android::internal::ToString(downlinkChannelNumber);
    os << ", uplinkChannelNumber: " << ::android::internal::ToString(uplinkChannelNumber);
    os << ", cellBandwidthDownlinkKhz: " << ::android::internal::ToString(cellBandwidthDownlinkKhz);
    os << ", cellBandwidthUplinkKhz: " << ::android::internal::ToString(cellBandwidthUplinkKhz);
    os << ", contextIds: " << ::android::internal::ToString(contextIds);
    os << ", physicalCellId: " << ::android::internal::ToString(physicalCellId);
    os << ", band: " << ::android::internal::ToString(band);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
