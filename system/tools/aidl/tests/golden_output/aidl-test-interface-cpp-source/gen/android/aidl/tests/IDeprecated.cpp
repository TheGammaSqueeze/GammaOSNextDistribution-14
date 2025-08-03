/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/IDeprecated.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IDeprecated.aidl
 */
#include <android/aidl/tests/IDeprecated.h>
#include <android/aidl/tests/BpDeprecated.h>
namespace android {
namespace aidl {
namespace tests {
DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_INTERFACE(Deprecated, "android.aidl.tests.IDeprecated")
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/BpDeprecated.h>
#include <android/aidl/tests/BnDeprecated.h>
#include <binder/Parcel.h>

namespace android {
namespace aidl {
namespace tests {

BpDeprecated::BpDeprecated(const ::android::sp<::android::IBinder>& _aidl_impl)
    : BpInterface<IDeprecated>(_aidl_impl){
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/BnDeprecated.h>
#include <binder/Parcel.h>
#include <binder/Stability.h>

namespace android {
namespace aidl {
namespace tests {

BnDeprecated::BnDeprecated()
{
  ::android::internal::Stability::markCompilationUnit(this);
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated"

::android::status_t BnDeprecated::onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  switch (_aidl_code) {
  default:
  {
    _aidl_ret_status = ::android::BBinder::onTransact(_aidl_code, _aidl_data, _aidl_reply, _aidl_flags);
  }
  break;
  }
  if (_aidl_ret_status == ::android::UNEXPECTED_NULL) {
    _aidl_ret_status = ::android::binder::Status::fromExceptionCode(::android::binder::Status::EX_NULL_POINTER).writeOverParcel(_aidl_reply);
  }
  return _aidl_ret_status;
}

#pragma clang diagnostic pop

}  // namespace tests
}  // namespace aidl
}  // namespace android
