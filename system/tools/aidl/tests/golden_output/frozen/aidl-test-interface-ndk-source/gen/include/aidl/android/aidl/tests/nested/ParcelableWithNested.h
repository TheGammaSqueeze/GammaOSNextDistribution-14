/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/nested/ParcelableWithNested.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/ParcelableWithNested.aidl
 */
#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/tests/nested/ParcelableWithNested.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace nested {
class ParcelableWithNested {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Status : int8_t {
    OK = 0,
    NOT_OK = 1,
  };

  ::aidl::android::aidl::tests::nested::ParcelableWithNested::Status status = ::aidl::android::aidl::tests::nested::ParcelableWithNested::Status::OK;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const ParcelableWithNested& _rhs) const {
    return std::tie(status) == std::tie(_rhs.status);
  }
  inline bool operator<(const ParcelableWithNested& _rhs) const {
    return std::tie(status) < std::tie(_rhs.status);
  }
  inline bool operator!=(const ParcelableWithNested& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const ParcelableWithNested& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const ParcelableWithNested& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const ParcelableWithNested& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "ParcelableWithNested{";
    _aidl_os << "status: " << ::android::internal::ToString(status);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace nested {
[[nodiscard]] static inline std::string toString(ParcelableWithNested::Status val) {
  switch(val) {
  case ParcelableWithNested::Status::OK:
    return "OK";
  case ParcelableWithNested::Status::NOT_OK:
    return "NOT_OK";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::tests::nested::ParcelableWithNested::Status, 2> enum_values<aidl::android::aidl::tests::nested::ParcelableWithNested::Status> = {
  aidl::android::aidl::tests::nested::ParcelableWithNested::Status::OK,
  aidl::android::aidl::tests::nested::ParcelableWithNested::Status::NOT_OK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
