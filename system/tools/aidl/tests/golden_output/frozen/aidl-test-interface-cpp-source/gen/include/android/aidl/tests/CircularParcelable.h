/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/CircularParcelable.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/CircularParcelable.aidl
 */
#pragma once

#include <android/aidl/tests/ITestService.h>
#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <optional>
#include <tuple>
#include <utils/String16.h>

namespace android::aidl::tests {
class ITestService;
}  // namespace android::aidl::tests
namespace android {
namespace aidl {
namespace tests {
class CircularParcelable : public ::android::Parcelable {
public:
  ::android::sp<::android::aidl::tests::ITestService> testService;
  inline bool operator==(const CircularParcelable& _rhs) const {
    return std::tie(testService) == std::tie(_rhs.testService);
  }
  inline bool operator<(const CircularParcelable& _rhs) const {
    return std::tie(testService) < std::tie(_rhs.testService);
  }
  inline bool operator!=(const CircularParcelable& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const CircularParcelable& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const CircularParcelable& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const CircularParcelable& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.CircularParcelable");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "CircularParcelable{";
    _aidl_os << "testService: " << ::android::internal::ToString(testService);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class CircularParcelable
}  // namespace tests
}  // namespace aidl
}  // namespace android
