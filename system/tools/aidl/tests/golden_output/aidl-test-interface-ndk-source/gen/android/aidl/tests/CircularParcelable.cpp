/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/CircularParcelable.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/CircularParcelable.aidl
 */
#include "aidl/android/aidl/tests/CircularParcelable.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/aidl/tests/BnCircular.h>
#include <aidl/android/aidl/tests/BnNamedCallback.h>
#include <aidl/android/aidl/tests/BnNewName.h>
#include <aidl/android/aidl/tests/BnOldName.h>
#include <aidl/android/aidl/tests/BnTestService.h>
#include <aidl/android/aidl/tests/BpCircular.h>
#include <aidl/android/aidl/tests/BpNamedCallback.h>
#include <aidl/android/aidl/tests/BpNewName.h>
#include <aidl/android/aidl/tests/BpOldName.h>
#include <aidl/android/aidl/tests/BpTestService.h>
#include <aidl/android/aidl/tests/ICircular.h>
#include <aidl/android/aidl/tests/INamedCallback.h>
#include <aidl/android/aidl/tests/INewName.h>
#include <aidl/android/aidl/tests/IOldName.h>
#include <aidl/android/aidl/tests/ITestService.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
const char* CircularParcelable::descriptor = "android.aidl.tests.CircularParcelable";

binder_status_t CircularParcelable::readFromParcel(const AParcel* _aidl_parcel) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  int32_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  int32_t _aidl_parcelable_size = 0;
  _aidl_ret_status = AParcel_readInt32(_aidl_parcel, &_aidl_parcelable_size);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (_aidl_parcelable_size < 4) return STATUS_BAD_VALUE;
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return STATUS_BAD_VALUE;
  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_parcel, &testService);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
binder_status_t CircularParcelable::writeToParcel(AParcel* _aidl_parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  _aidl_ret_status = AParcel_writeInt32(_aidl_parcel, 0);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_parcel, testService);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  size_t _aidl_end_pos = AParcel_getDataPosition(_aidl_parcel);
  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos);
  AParcel_writeInt32(_aidl_parcel, _aidl_end_pos - _aidl_start_pos);
  AParcel_setDataPosition(_aidl_parcel, _aidl_end_pos);
  return _aidl_ret_status;
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
