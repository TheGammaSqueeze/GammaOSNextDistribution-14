#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/modem/NvItem.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class NvWriteItem {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::modem::NvItem itemId = ::aidl::android::hardware::radio::modem::NvItem(0);
  std::string value;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NvWriteItem& rhs) const {
    return std::tie(itemId, value) != std::tie(rhs.itemId, rhs.value);
  }
  inline bool operator<(const NvWriteItem& rhs) const {
    return std::tie(itemId, value) < std::tie(rhs.itemId, rhs.value);
  }
  inline bool operator<=(const NvWriteItem& rhs) const {
    return std::tie(itemId, value) <= std::tie(rhs.itemId, rhs.value);
  }
  inline bool operator==(const NvWriteItem& rhs) const {
    return std::tie(itemId, value) == std::tie(rhs.itemId, rhs.value);
  }
  inline bool operator>(const NvWriteItem& rhs) const {
    return std::tie(itemId, value) > std::tie(rhs.itemId, rhs.value);
  }
  inline bool operator>=(const NvWriteItem& rhs) const {
    return std::tie(itemId, value) >= std::tie(rhs.itemId, rhs.value);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NvWriteItem{";
    os << "itemId: " << ::android::internal::ToString(itemId);
    os << ", value: " << ::android::internal::ToString(value);
    os << "}";
    return os.str();
  }
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
