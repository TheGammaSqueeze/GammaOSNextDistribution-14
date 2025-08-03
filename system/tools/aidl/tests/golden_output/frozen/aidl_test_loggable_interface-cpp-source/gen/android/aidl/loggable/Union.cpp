/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/staging/android/aidl/loggable/Union.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/Union.aidl
 */
#include <android/aidl/loggable/Union.h>

namespace android {
namespace aidl {
namespace loggable {
::android::status_t Union::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_tag)) != ::android::OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case num: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<num>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<num>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case str: {
    ::std::string _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readUtf8FromUtf16(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::std::string>) {
      set<str>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<str>(std::move(_aidl_value));
    }
    return ::android::OK; }
  }
  return ::android::BAD_VALUE;
}
::android::status_t Union::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = _aidl_parcel->writeInt32(static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != ::android::OK) return _aidl_ret_status;
  switch (getTag()) {
  case num: return _aidl_parcel->writeInt32(get<num>());
  case str: return _aidl_parcel->writeUtf8AsUtf16(get<str>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}
}  // namespace loggable
}  // namespace aidl
}  // namespace android
