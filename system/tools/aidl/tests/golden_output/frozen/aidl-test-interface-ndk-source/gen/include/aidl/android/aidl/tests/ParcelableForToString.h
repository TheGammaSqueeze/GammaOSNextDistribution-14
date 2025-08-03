/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ParcelableForToString.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ParcelableForToString.aidl
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
#include <aidl/android/aidl/tests/GenericStructuredParcelable.h>
#include <aidl/android/aidl/tests/IntEnum.h>
#include <aidl/android/aidl/tests/OtherParcelableForToString.h>
#include <aidl/android/aidl/tests/StructuredParcelable.h>
#include <aidl/android/aidl/tests/Union.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::aidl::tests {
template <typename T, typename U, typename B>
class GenericStructuredParcelable;
class OtherParcelableForToString;
class StructuredParcelable;
}  // namespace aidl::android::aidl::tests
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class ParcelableForToString {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t intValue = 0;
  std::vector<int32_t> intArray;
  int64_t longValue = 0L;
  std::vector<int64_t> longArray;
  double doubleValue = 0.000000;
  std::vector<double> doubleArray;
  float floatValue = 0.000000f;
  std::vector<float> floatArray;
  int8_t byteValue = 0;
  std::vector<uint8_t> byteArray;
  bool booleanValue = false;
  std::vector<bool> booleanArray;
  std::string stringValue;
  std::vector<std::string> stringArray;
  std::vector<std::string> stringList;
  ::aidl::android::aidl::tests::OtherParcelableForToString parcelableValue;
  std::vector<::aidl::android::aidl::tests::OtherParcelableForToString> parcelableArray;
  ::aidl::android::aidl::tests::IntEnum enumValue = ::aidl::android::aidl::tests::IntEnum::FOO;
  std::vector<::aidl::android::aidl::tests::IntEnum> enumArray;
  std::vector<std::string> nullArray;
  std::vector<std::string> nullList;
  ::aidl::android::aidl::tests::GenericStructuredParcelable<int32_t, ::aidl::android::aidl::tests::StructuredParcelable, ::aidl::android::aidl::tests::IntEnum> parcelableGeneric;
  ::aidl::android::aidl::tests::Union unionValue;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

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

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
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
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
