#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>
#include <vector>

namespace android {
namespace gui {
class PullAtomData : public ::android::Parcelable {
public:
  ::std::vector<uint8_t> data;
  bool success = false;
  inline bool operator!=(const PullAtomData& rhs) const {
    return std::tie(data, success) != std::tie(rhs.data, rhs.success);
  }
  inline bool operator<(const PullAtomData& rhs) const {
    return std::tie(data, success) < std::tie(rhs.data, rhs.success);
  }
  inline bool operator<=(const PullAtomData& rhs) const {
    return std::tie(data, success) <= std::tie(rhs.data, rhs.success);
  }
  inline bool operator==(const PullAtomData& rhs) const {
    return std::tie(data, success) == std::tie(rhs.data, rhs.success);
  }
  inline bool operator>(const PullAtomData& rhs) const {
    return std::tie(data, success) > std::tie(rhs.data, rhs.success);
  }
  inline bool operator>=(const PullAtomData& rhs) const {
    return std::tie(data, success) >= std::tie(rhs.data, rhs.success);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.PullAtomData");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "PullAtomData{";
    os << "data: " << ::android::internal::ToString(data);
    os << ", success: " << ::android::internal::ToString(success);
    os << "}";
    return os.str();
  }
};  // class PullAtomData
}  // namespace gui
}  // namespace android
