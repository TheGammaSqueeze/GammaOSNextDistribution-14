/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/Union.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/Union.aidl
 */
#include <android/aidl/tests/Union.h>

namespace android {
namespace aidl {
namespace tests {
const ::std::string& Union::S1() {
  static const ::std::string value("a string constant in union");
  return value;
}
::android::status_t Union::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_tag)) != ::android::OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case ns: {
    ::std::vector<int32_t> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32Vector(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::std::vector<int32_t>>) {
      set<ns>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ns>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case n: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<n>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<n>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case m: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<m>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<m>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case s: {
    ::std::string _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readUtf8FromUtf16(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::std::string>) {
      set<s>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<s>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case ibinder: {
    ::android::sp<::android::IBinder> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readNullableStrongBinder(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::android::sp<::android::IBinder>>) {
      set<ibinder>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ibinder>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case ss: {
    ::std::vector<::std::string> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readUtf8VectorFromUtf16Vector(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::std::vector<::std::string>>) {
      set<ss>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ss>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case be: {
    ::android::aidl::tests::ByteEnum _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readByte(reinterpret_cast<int8_t *>(&_aidl_value))) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::android::aidl::tests::ByteEnum>) {
      set<be>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<be>(std::move(_aidl_value));
    }
    return ::android::OK; }
  }
  return ::android::BAD_VALUE;
}
::android::status_t Union::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = _aidl_parcel->writeInt32(static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != ::android::OK) return _aidl_ret_status;
  switch (getTag()) {
  case ns: return _aidl_parcel->writeInt32Vector(get<ns>());
  case n: return _aidl_parcel->writeInt32(get<n>());
  case m: return _aidl_parcel->writeInt32(get<m>());
  case s: return _aidl_parcel->writeUtf8AsUtf16(get<s>());
  case ibinder: return _aidl_parcel->writeStrongBinder(get<ibinder>());
  case ss: return _aidl_parcel->writeUtf8VectorAsUtf16Vector(get<ss>());
  case be: return _aidl_parcel->writeByte(static_cast<int8_t>(get<be>()));
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
