/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/extension/MyExt2.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/extension/MyExt2.aidl
 */
#pragma once

#include <android/aidl/tests/extension/MyExt.h>
#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>
#include <utils/String16.h>

namespace android::aidl::tests::extension {
class MyExt;
}  // namespace android::aidl::tests::extension
namespace android {
namespace aidl {
namespace tests {
namespace extension {
class MyExt2 : public ::android::Parcelable {
public:
  int32_t a = 0;
  ::android::aidl::tests::extension::MyExt b;
  ::std::string c;
  inline bool operator==(const MyExt2& _rhs) const {
    return std::tie(a, b, c) == std::tie(_rhs.a, _rhs.b, _rhs.c);
  }
  inline bool operator<(const MyExt2& _rhs) const {
    return std::tie(a, b, c) < std::tie(_rhs.a, _rhs.b, _rhs.c);
  }
  inline bool operator!=(const MyExt2& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const MyExt2& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const MyExt2& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const MyExt2& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.extension.MyExt2");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "MyExt2{";
    _aidl_os << "a: " << ::android::internal::ToString(a);
    _aidl_os << ", b: " << ::android::internal::ToString(b);
    _aidl_os << ", c: " << ::android::internal::ToString(c);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class MyExt2
}  // namespace extension
}  // namespace tests
}  // namespace aidl
}  // namespace android
