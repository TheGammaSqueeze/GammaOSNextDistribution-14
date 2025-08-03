#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace health {
class DiskStats {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t reads = 0L;
  int64_t readMerges = 0L;
  int64_t readSectors = 0L;
  int64_t readTicks = 0L;
  int64_t writes = 0L;
  int64_t writeMerges = 0L;
  int64_t writeSectors = 0L;
  int64_t writeTicks = 0L;
  int64_t ioInFlight = 0L;
  int64_t ioTicks = 0L;
  int64_t ioInQueue = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DiskStats& rhs) const {
    return std::tie(reads, readMerges, readSectors, readTicks, writes, writeMerges, writeSectors, writeTicks, ioInFlight, ioTicks, ioInQueue) != std::tie(rhs.reads, rhs.readMerges, rhs.readSectors, rhs.readTicks, rhs.writes, rhs.writeMerges, rhs.writeSectors, rhs.writeTicks, rhs.ioInFlight, rhs.ioTicks, rhs.ioInQueue);
  }
  inline bool operator<(const DiskStats& rhs) const {
    return std::tie(reads, readMerges, readSectors, readTicks, writes, writeMerges, writeSectors, writeTicks, ioInFlight, ioTicks, ioInQueue) < std::tie(rhs.reads, rhs.readMerges, rhs.readSectors, rhs.readTicks, rhs.writes, rhs.writeMerges, rhs.writeSectors, rhs.writeTicks, rhs.ioInFlight, rhs.ioTicks, rhs.ioInQueue);
  }
  inline bool operator<=(const DiskStats& rhs) const {
    return std::tie(reads, readMerges, readSectors, readTicks, writes, writeMerges, writeSectors, writeTicks, ioInFlight, ioTicks, ioInQueue) <= std::tie(rhs.reads, rhs.readMerges, rhs.readSectors, rhs.readTicks, rhs.writes, rhs.writeMerges, rhs.writeSectors, rhs.writeTicks, rhs.ioInFlight, rhs.ioTicks, rhs.ioInQueue);
  }
  inline bool operator==(const DiskStats& rhs) const {
    return std::tie(reads, readMerges, readSectors, readTicks, writes, writeMerges, writeSectors, writeTicks, ioInFlight, ioTicks, ioInQueue) == std::tie(rhs.reads, rhs.readMerges, rhs.readSectors, rhs.readTicks, rhs.writes, rhs.writeMerges, rhs.writeSectors, rhs.writeTicks, rhs.ioInFlight, rhs.ioTicks, rhs.ioInQueue);
  }
  inline bool operator>(const DiskStats& rhs) const {
    return std::tie(reads, readMerges, readSectors, readTicks, writes, writeMerges, writeSectors, writeTicks, ioInFlight, ioTicks, ioInQueue) > std::tie(rhs.reads, rhs.readMerges, rhs.readSectors, rhs.readTicks, rhs.writes, rhs.writeMerges, rhs.writeSectors, rhs.writeTicks, rhs.ioInFlight, rhs.ioTicks, rhs.ioInQueue);
  }
  inline bool operator>=(const DiskStats& rhs) const {
    return std::tie(reads, readMerges, readSectors, readTicks, writes, writeMerges, writeSectors, writeTicks, ioInFlight, ioTicks, ioInQueue) >= std::tie(rhs.reads, rhs.readMerges, rhs.readSectors, rhs.readTicks, rhs.writes, rhs.writeMerges, rhs.writeSectors, rhs.writeTicks, rhs.ioInFlight, rhs.ioTicks, rhs.ioInQueue);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DiskStats{";
    os << "reads: " << ::android::internal::ToString(reads);
    os << ", readMerges: " << ::android::internal::ToString(readMerges);
    os << ", readSectors: " << ::android::internal::ToString(readSectors);
    os << ", readTicks: " << ::android::internal::ToString(readTicks);
    os << ", writes: " << ::android::internal::ToString(writes);
    os << ", writeMerges: " << ::android::internal::ToString(writeMerges);
    os << ", writeSectors: " << ::android::internal::ToString(writeSectors);
    os << ", writeTicks: " << ::android::internal::ToString(writeTicks);
    os << ", ioInFlight: " << ::android::internal::ToString(ioInFlight);
    os << ", ioTicks: " << ::android::internal::ToString(ioTicks);
    os << ", ioInQueue: " << ::android::internal::ToString(ioInQueue);
    os << "}";
    return os.str();
  }
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
