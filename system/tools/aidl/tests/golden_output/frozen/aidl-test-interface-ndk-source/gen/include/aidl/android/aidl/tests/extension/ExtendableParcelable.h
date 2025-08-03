/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/extension/ExtendableParcelable.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/extension/ExtendableParcelable.aidl
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
namespace extension {
class ExtendableParcelable {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t a = 0;
  std::string b;
  ::ndk::AParcelableHolder ext{::ndk::STABILITY_LOCAL};
  int64_t c = 0L;
  ::ndk::AParcelableHolder ext2{::ndk::STABILITY_LOCAL};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const ExtendableParcelable& _rhs) const {
    return std::tie(a, b, ext, c, ext2) == std::tie(_rhs.a, _rhs.b, _rhs.ext, _rhs.c, _rhs.ext2);
  }
  inline bool operator<(const ExtendableParcelable& _rhs) const {
    return std::tie(a, b, ext, c, ext2) < std::tie(_rhs.a, _rhs.b, _rhs.ext, _rhs.c, _rhs.ext2);
  }
  inline bool operator!=(const ExtendableParcelable& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const ExtendableParcelable& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const ExtendableParcelable& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const ExtendableParcelable& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "ExtendableParcelable{";
    _aidl_os << "a: " << ::android::internal::ToString(a);
    _aidl_os << ", b: " << ::android::internal::ToString(b);
    _aidl_os << ", ext: " << ::android::internal::ToString(ext);
    _aidl_os << ", c: " << ::android::internal::ToString(c);
    _aidl_os << ", ext2: " << ::android::internal::ToString(ext2);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace extension
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
