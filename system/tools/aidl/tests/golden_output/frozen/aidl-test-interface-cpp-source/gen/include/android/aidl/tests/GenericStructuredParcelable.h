/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/GenericStructuredParcelable.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/GenericStructuredParcelable.aidl
 */
#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace aidl {
namespace tests {
template <typename T, typename U, typename B>
class GenericStructuredParcelable : public ::android::Parcelable {
public:
  int32_t a = 0;
  int32_t b = 0;
  inline bool operator==(const GenericStructuredParcelable& _rhs) const {
    return std::tie(a, b) == std::tie(_rhs.a, _rhs.b);
  }
  inline bool operator<(const GenericStructuredParcelable& _rhs) const {
    return std::tie(a, b) < std::tie(_rhs.a, _rhs.b);
  }
  inline bool operator!=(const GenericStructuredParcelable& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const GenericStructuredParcelable& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const GenericStructuredParcelable& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const GenericStructuredParcelable& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.GenericStructuredParcelable");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "GenericStructuredParcelable{";
    _aidl_os << "a: " << ::android::internal::ToString(a);
    _aidl_os << ", b: " << ::android::internal::ToString(b);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class GenericStructuredParcelable
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/GenericStructuredParcelable.h>

namespace android {
namespace aidl {
namespace tests {
template <typename T, typename U, typename B>
::android::status_t GenericStructuredParcelable<T,U,B>::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  size_t _aidl_start_pos = _aidl_parcel->dataPosition();
  int32_t _aidl_parcelable_raw_size = 0;
  _aidl_ret_status = _aidl_parcel->readInt32(&_aidl_parcelable_raw_size);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcelable_raw_size < 4) return ::android::BAD_VALUE;
  size_t _aidl_parcelable_size = static_cast<size_t>(_aidl_parcelable_raw_size);
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return ::android::BAD_VALUE;
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readInt32(&a);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readInt32(&b);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
template <typename T, typename U, typename B>
::android::status_t GenericStructuredParcelable<T,U,B>::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = ::android::OK;
  size_t _aidl_start_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->writeInt32(0);
  _aidl_ret_status = _aidl_parcel->writeInt32(a);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->writeInt32(b);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  size_t _aidl_end_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->setDataPosition(_aidl_start_pos);
  _aidl_parcel->writeInt32(static_cast<int32_t>(_aidl_end_pos - _aidl_start_pos));
  _aidl_parcel->setDataPosition(_aidl_end_pos);
  return _aidl_ret_status;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
