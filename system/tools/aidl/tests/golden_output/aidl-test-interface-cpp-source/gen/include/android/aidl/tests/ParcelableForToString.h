/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ParcelableForToString.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ParcelableForToString.aidl
 */
#pragma once

#include <android/aidl/tests/GenericStructuredParcelable.h>
#include <android/aidl/tests/IntEnum.h>
#include <android/aidl/tests/OtherParcelableForToString.h>
#include <android/aidl/tests/StructuredParcelable.h>
#include <android/aidl/tests/Union.h>
#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>
#include <vector>

namespace android::aidl::tests {
template <typename T, typename U, typename B>
class GenericStructuredParcelable;
class OtherParcelableForToString;
class StructuredParcelable;
}  // namespace android::aidl::tests
namespace android {
namespace aidl {
namespace tests {
class ParcelableForToString : public ::android::Parcelable {
public:
  int32_t intValue = 0;
  ::std::vector<int32_t> intArray;
  int64_t longValue = 0L;
  ::std::vector<int64_t> longArray;
  double doubleValue = 0.000000;
  ::std::vector<double> doubleArray;
  float floatValue = 0.000000f;
  ::std::vector<float> floatArray;
  int8_t byteValue = 0;
  ::std::vector<uint8_t> byteArray;
  bool booleanValue = false;
  ::std::vector<bool> booleanArray;
  ::android::String16 stringValue;
  ::std::vector<::android::String16> stringArray;
  ::std::vector<::android::String16> stringList;
  ::android::aidl::tests::OtherParcelableForToString parcelableValue;
  ::std::vector<::android::aidl::tests::OtherParcelableForToString> parcelableArray;
  ::android::aidl::tests::IntEnum enumValue = ::android::aidl::tests::IntEnum::FOO;
  ::std::vector<::android::aidl::tests::IntEnum> enumArray;
  ::std::vector<::android::String16> nullArray;
  ::std::vector<::android::String16> nullList;
  ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum> parcelableGeneric;
  ::android::aidl::tests::Union unionValue;
  inline bool operator==(const ParcelableForToString& _rhs) const {
    return std::tie(intValue, intArray, longValue, longArray, doubleValue, doubleArray, floatValue, floatArray, byteValue, byteArray, booleanValue, booleanArray, stringValue, stringArray, stringList, parcelableValue, parcelableArray, enumValue, enumArray, nullArray, nullList, parcelableGeneric, unionValue) == std::tie(_rhs.intValue, _rhs.intArray, _rhs.longValue, _rhs.longArray, _rhs.doubleValue, _rhs.doubleArray, _rhs.floatValue, _rhs.floatArray, _rhs.byteValue, _rhs.byteArray, _rhs.booleanValue, _rhs.booleanArray, _rhs.stringValue, _rhs.stringArray, _rhs.stringList, _rhs.parcelableValue, _rhs.parcelableArray, _rhs.enumValue, _rhs.enumArray, _rhs.nullArray, _rhs.nullList, _rhs.parcelableGeneric, _rhs.unionValue);
  }
  inline bool operator<(const ParcelableForToString& _rhs) const {
    return std::tie(intValue, intArray, longValue, longArray, doubleValue, doubleArray, floatValue, floatArray, byteValue, byteArray, booleanValue, booleanArray, stringValue, stringArray, stringList, parcelableValue, parcelableArray, enumValue, enumArray, nullArray, nullList, parcelableGeneric, unionValue) < std::tie(_rhs.intValue, _rhs.intArray, _rhs.longValue, _rhs.longArray, _rhs.doubleValue, _rhs.doubleArray, _rhs.floatValue, _rhs.floatArray, _rhs.byteValue, _rhs.byteArray, _rhs.booleanValue, _rhs.booleanArray, _rhs.stringValue, _rhs.stringArray, _rhs.stringList, _rhs.parcelableValue, _rhs.parcelableArray, _rhs.enumValue, _rhs.enumArray, _rhs.nullArray, _rhs.nullList, _rhs.parcelableGeneric, _rhs.unionValue);
  }
  inline bool operator!=(const ParcelableForToString& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const ParcelableForToString& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const ParcelableForToString& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const ParcelableForToString& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.ParcelableForToString");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "ParcelableForToString{";
    _aidl_os << "intValue: " << ::android::internal::ToString(intValue);
    _aidl_os << ", intArray: " << ::android::internal::ToString(intArray);
    _aidl_os << ", longValue: " << ::android::internal::ToString(longValue);
    _aidl_os << ", longArray: " << ::android::internal::ToString(longArray);
    _aidl_os << ", doubleValue: " << ::android::internal::ToString(doubleValue);
    _aidl_os << ", doubleArray: " << ::android::internal::ToString(doubleArray);
    _aidl_os << ", floatValue: " << ::android::internal::ToString(floatValue);
    _aidl_os << ", floatArray: " << ::android::internal::ToString(floatArray);
    _aidl_os << ", byteValue: " << ::android::internal::ToString(byteValue);
    _aidl_os << ", byteArray: " << ::android::internal::ToString(byteArray);
    _aidl_os << ", booleanValue: " << ::android::internal::ToString(booleanValue);
    _aidl_os << ", booleanArray: " << ::android::internal::ToString(booleanArray);
    _aidl_os << ", stringValue: " << ::android::internal::ToString(stringValue);
    _aidl_os << ", stringArray: " << ::android::internal::ToString(stringArray);
    _aidl_os << ", stringList: " << ::android::internal::ToString(stringList);
    _aidl_os << ", parcelableValue: " << ::android::internal::ToString(parcelableValue);
    _aidl_os << ", parcelableArray: " << ::android::internal::ToString(parcelableArray);
    _aidl_os << ", enumValue: " << ::android::internal::ToString(enumValue);
    _aidl_os << ", enumArray: " << ::android::internal::ToString(enumArray);
    _aidl_os << ", nullArray: " << ::android::internal::ToString(nullArray);
    _aidl_os << ", nullList: " << ::android::internal::ToString(nullList);
    _aidl_os << ", parcelableGeneric: " << ::android::internal::ToString(parcelableGeneric);
    _aidl_os << ", unionValue: " << ::android::internal::ToString(unionValue);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class ParcelableForToString
}  // namespace tests
}  // namespace aidl
}  // namespace android
