/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/Union.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/Union.aidl
 */
#include "aidl/android/aidl/tests/Union.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
const char* Union::descriptor = "android.aidl.tests.Union";

const char* Union::S1 = "a string constant in union";
binder_status_t Union::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case ns: {
    std::vector<int32_t> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<int32_t>>) {
      set<ns>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ns>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case n: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<n>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<n>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case m: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<m>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<m>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case s: {
    std::string _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::string>) {
      set<s>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<s>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case ibinder: {
    ::ndk::SpAIBinder _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readNullableData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::ndk::SpAIBinder>) {
      set<ibinder>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ibinder>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case ss: {
    std::vector<std::string> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<std::string>>) {
      set<ss>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ss>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case be: {
    ::aidl::android::aidl::tests::ByteEnum _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::aidl::tests::ByteEnum>) {
      set<be>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<be>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t Union::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case ns: return ::ndk::AParcel_writeData(_parcel, get<ns>());
  case n: return ::ndk::AParcel_writeData(_parcel, get<n>());
  case m: return ::ndk::AParcel_writeData(_parcel, get<m>());
  case s: return ::ndk::AParcel_writeData(_parcel, get<s>());
  case ibinder: return ::ndk::AParcel_writeNullableData(_parcel, get<ibinder>());
  case ss: return ::ndk::AParcel_writeData(_parcel, get<ss>());
  case be: return ::ndk::AParcel_writeData(_parcel, get<be>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
