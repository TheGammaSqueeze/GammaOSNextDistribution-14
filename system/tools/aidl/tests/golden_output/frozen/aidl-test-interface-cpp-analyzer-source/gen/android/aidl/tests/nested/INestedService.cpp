/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp-analyzer -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-analyzer-source/gen/staging/android/aidl/tests/nested/INestedService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-analyzer-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-analyzer-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
#include <iostream>
#include <binder/Parcel.h>
#include <android/binder_to_string.h>
#include <android/aidl/tests/nested/INestedService.h>
namespace {
android::status_t analyzeINestedService(uint32_t _aidl_code, const android::Parcel& _aidl_data, const android::Parcel& _aidl_reply) {
  android::status_t _aidl_ret_status;
  switch(_aidl_code) {
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 0:
    {
      std::cout << "INestedService.flipStatus()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.nested.INestedService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::nested::ParcelableWithNested in_p;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::nested::INestedService::Result _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelable(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_p);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument p from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    p: " << ::android::internal::ToString(in_p) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 1:
    {
      std::cout << "INestedService.flipStatusWithCallback()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.nested.INestedService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::nested::ParcelableWithNested::Status in_status;
      ::android::sp<::android::aidl::tests::nested::INestedService::ICallback> in_cb;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readByte(reinterpret_cast<int8_t *>(&in_status));
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument status from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readStrongBinder(&in_cb);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument cb from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    status: " << ::android::internal::ToString(in_status) << std::endl;
      std::cout << "    cb: " << ::android::internal::ToString(in_cb) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    default:
    {
      std::cout << "  Transaction code " << _aidl_code << " not known." << std::endl;
    _aidl_ret_status = android::UNKNOWN_TRANSACTION;
    }
  }
  return _aidl_ret_status;
  // To prevent unused variable warnings
  (void)_aidl_ret_status; (void)_aidl_data; (void)_aidl_reply;
}

} // namespace

#include <Analyzer.h>
using android::aidl::Analyzer;
__attribute__((constructor)) static void addAnalyzer() {
  Analyzer::installAnalyzer(std::make_unique<Analyzer>("android.aidl.tests.nested.INestedService", "INestedService", &analyzeINestedService));
}
