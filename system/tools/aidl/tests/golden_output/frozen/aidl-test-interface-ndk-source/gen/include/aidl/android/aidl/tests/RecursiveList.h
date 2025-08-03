/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/RecursiveList.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/RecursiveList.aidl
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
#include <aidl/android/aidl/tests/RecursiveList.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::aidl::tests {
class RecursiveList;
}  // namespace aidl::android::aidl::tests
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class RecursiveList {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t value = 0;
  std::unique_ptr<::aidl::android::aidl::tests::RecursiveList> next;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const RecursiveList& _rhs) const {
    return std::tie(value, next) == std::tie(_rhs.value, _rhs.next);
  }
  inline bool operator<(const RecursiveList& _rhs) const {
    return std::tie(value, next) < std::tie(_rhs.value, _rhs.next);
  }
  inline bool operator!=(const RecursiveList& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const RecursiveList& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const RecursiveList& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const RecursiveList& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "RecursiveList{";
    _aidl_os << "value: " << ::android::internal::ToString(value);
    _aidl_os << ", next: " << ::android::internal::ToString(next);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
