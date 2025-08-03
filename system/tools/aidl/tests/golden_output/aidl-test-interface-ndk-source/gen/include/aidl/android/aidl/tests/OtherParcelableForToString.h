/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/OtherParcelableForToString.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/OtherParcelableForToString.aidl
 */
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
namespace aidl {
namespace tests {
class OtherParcelableForToString {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string field;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const OtherParcelableForToString& _rhs) const {
    return std::tie(field) == std::tie(_rhs.field);
  }
  inline bool operator<(const OtherParcelableForToString& _rhs) const {
    return std::tie(field) < std::tie(_rhs.field);
  }
  inline bool operator!=(const OtherParcelableForToString& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const OtherParcelableForToString& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const OtherParcelableForToString& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const OtherParcelableForToString& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "OtherParcelableForToString{";
    _aidl_os << "field: " << ::android::internal::ToString(field);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
