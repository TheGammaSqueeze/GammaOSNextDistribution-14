/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ICircular.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICircular.aidl
 */
#include <android/aidl/tests/ICircular.h>
#include <android/aidl/tests/BpCircular.h>
namespace android {
namespace aidl {
namespace tests {
DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_INTERFACE(Circular, "android.aidl.tests.ICircular")
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/BpCircular.h>
#include <android/aidl/tests/BnCircular.h>
#include <binder/Parcel.h>

namespace android {
namespace aidl {
namespace tests {

BpCircular::BpCircular(const ::android::sp<::android::IBinder>& _aidl_impl)
    : BpInterface<ICircular>(_aidl_impl){
}

::android::binder::Status BpCircular::GetTestService(::android::sp<::android::aidl::tests::ITestService>* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ICircular::GetTestService::cppClient");
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(BnCircular::TRANSACTION_GetTestService, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && ICircular::getDefaultImpl()) [[unlikely]] {
     return ICircular::getDefaultImpl()->GetTestService(_aidl_return);
  }
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_status.readFromParcel(_aidl_reply);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  if (!_aidl_status.isOk()) {
    return _aidl_status;
  }
  _aidl_ret_status = _aidl_reply.readNullableStrongBinder(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  return _aidl_status;
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/BnCircular.h>
#include <binder/Parcel.h>
#include <binder/Stability.h>

namespace android {
namespace aidl {
namespace tests {

BnCircular::BnCircular()
{
  ::android::internal::Stability::markCompilationUnit(this);
}

::android::status_t BnCircular::onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  switch (_aidl_code) {
  case BnCircular::TRANSACTION_GetTestService:
  {
    ::android::sp<::android::aidl::tests::ITestService> _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ICircular::GetTestService::cppServer");
    ::android::binder::Status _aidl_status(GetTestService(&_aidl_return));
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeStrongBinder(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
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

}  // namespace tests
}  // namespace aidl
}  // namespace android
