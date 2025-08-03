/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/unions/EnumUnion.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/unions/EnumUnion.aidl
 */
#include <android/aidl/tests/unions/EnumUnion.h>

namespace android {
namespace aidl {
namespace tests {
namespace unions {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
::android::status_t EnumUnion::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_tag)) != ::android::OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case intEnum: {
    ::android::aidl::tests::IntEnum _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(reinterpret_cast<int32_t *>(&_aidl_value))) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::android::aidl::tests::IntEnum>) {
      set<intEnum>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<intEnum>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case longEnum: {
    ::android::aidl::tests::LongEnum _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt64(reinterpret_cast<int64_t *>(&_aidl_value))) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::android::aidl::tests::LongEnum>) {
      set<longEnum>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<longEnum>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case deprecatedField: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<deprecatedField>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<deprecatedField>(std::move(_aidl_value));
    }
    return ::android::OK; }
  }
  return ::android::BAD_VALUE;
}
::android::status_t EnumUnion::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = _aidl_parcel->writeInt32(static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != ::android::OK) return _aidl_ret_status;
  switch (getTag()) {
  case intEnum: return _aidl_parcel->writeInt32(static_cast<int32_t>(get<intEnum>()));
  case longEnum: return _aidl_parcel->writeInt64(static_cast<int64_t>(get<longEnum>()));
  #pragma clang diagnostic push
  #pragma clang diagnostic ignored "-Wdeprecated-declarations"
  case deprecatedField: return _aidl_parcel->writeInt32(get<deprecatedField>());
  #pragma clang diagnostic pop
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}
#pragma clang diagnostic pop
}  // namespace unions
}  // namespace tests
}  // namespace aidl
}  // namespace android
