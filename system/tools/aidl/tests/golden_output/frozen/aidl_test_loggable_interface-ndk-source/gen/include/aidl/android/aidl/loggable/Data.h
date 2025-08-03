/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/staging/android/aidl/loggable/Data.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/Data.aidl
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
#include <aidl/android/aidl/loggable/Enum.h>
#include <aidl/android/aidl/loggable/Union.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace aidl {
namespace loggable {
class Data {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t num = 0;
  std::string str;
  ::aidl::android::aidl::loggable::Union nestedUnion;
  ::aidl::android::aidl::loggable::Enum nestedEnum = ::aidl::android::aidl::loggable::Enum::FOO;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const Data& _rhs) const {
    return std::tie(num, str, nestedUnion, nestedEnum) == std::tie(_rhs.num, _rhs.str, _rhs.nestedUnion, _rhs.nestedEnum);
  }
  inline bool operator<(const Data& _rhs) const {
    return std::tie(num, str, nestedUnion, nestedEnum) < std::tie(_rhs.num, _rhs.str, _rhs.nestedUnion, _rhs.nestedEnum);
  }
  inline bool operator!=(const Data& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const Data& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const Data& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const Data& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "Data{";
    _aidl_os << "num: " << ::android::internal::ToString(num);
    _aidl_os << ", str: " << ::android::internal::ToString(str);
    _aidl_os << ", nestedUnion: " << ::android::internal::ToString(nestedUnion);
    _aidl_os << ", nestedEnum: " << ::android::internal::ToString(nestedEnum);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace loggable
}  // namespace aidl
}  // namespace android
}  // namespace aidl
