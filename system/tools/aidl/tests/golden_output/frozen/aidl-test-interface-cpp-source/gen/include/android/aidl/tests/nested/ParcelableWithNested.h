/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/nested/ParcelableWithNested.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/ParcelableWithNested.aidl
 */
#pragma once

#include <android/aidl/tests/nested/ParcelableWithNested.h>
#include <android/binder_to_string.h>
#include <array>
#include <binder/Enums.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace aidl {
namespace tests {
namespace nested {
class ParcelableWithNested : public ::android::Parcelable {
public:
  enum class Status : int8_t {
    OK = 0,
    NOT_OK = 1,
  };
  ::android::aidl::tests::nested::ParcelableWithNested::Status status = ::android::aidl::tests::nested::ParcelableWithNested::Status::OK;
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

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.ParcelableWithNested");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "ParcelableWithNested{";
    _aidl_os << "status: " << ::android::internal::ToString(status);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class ParcelableWithNested
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
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
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::tests::nested::ParcelableWithNested::Status, 2> enum_values<::android::aidl::tests::nested::ParcelableWithNested::Status> = {
  ::android::aidl::tests::nested::ParcelableWithNested::Status::OK,
  ::android::aidl::tests::nested::ParcelableWithNested::Status::NOT_OK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
