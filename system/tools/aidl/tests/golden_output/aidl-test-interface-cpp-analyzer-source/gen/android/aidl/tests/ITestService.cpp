/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp-analyzer -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-analyzer-source/gen/staging/android/aidl/tests/ITestService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-analyzer-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-analyzer-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#include <iostream>
#include <binder/Parcel.h>
#include <android/binder_to_string.h>
#include <android/aidl/tests/ITestService.h>
namespace {
android::status_t analyzeITestService(uint32_t _aidl_code, const android::Parcel& _aidl_data, const android::Parcel& _aidl_reply) {
  android::status_t _aidl_ret_status;
  switch(_aidl_code) {
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 0:
    {
      std::cout << "ITestService.UnimplementedMethod()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      int32_t in_arg;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      int32_t _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt32(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt32(&in_arg);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument arg from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    arg: " << ::android::internal::ToString(in_arg) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 1:
    {
      std::cout << "ITestService.Deprecated()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
      } while(false);
      std::cout << "  arguments: " << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 2:
    {
      std::cout << "ITestService.TestOneway()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
      } while(false);
      std::cout << "  arguments: " << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 3:
    {
      std::cout << "ITestService.RepeatBoolean()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      bool in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      bool _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBool(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readBool(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 4:
    {
      std::cout << "ITestService.RepeatByte()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      int8_t in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      int8_t _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readByte(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readByte(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 5:
    {
      std::cout << "ITestService.RepeatChar()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      char16_t in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      char16_t _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readChar(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readChar(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 6:
    {
      std::cout << "ITestService.RepeatInt()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      int32_t in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      int32_t _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt32(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt32(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 7:
    {
      std::cout << "ITestService.RepeatLong()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      int64_t in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      int64_t _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt64(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt64(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 8:
    {
      std::cout << "ITestService.RepeatFloat()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      float in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      float _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readFloat(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readFloat(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 9:
    {
      std::cout << "ITestService.RepeatDouble()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      double in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      double _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readDouble(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readDouble(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 10:
    {
      std::cout << "ITestService.RepeatString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::String16 in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::String16 _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readString16(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 11:
    {
      std::cout << "ITestService.RepeatByteEnum()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::ByteEnum in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::ByteEnum _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readByte(reinterpret_cast<int8_t *>(&_aidl_return));
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readByte(reinterpret_cast<int8_t *>(&in_token));
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 12:
    {
      std::cout << "ITestService.RepeatIntEnum()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::IntEnum in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::IntEnum _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt32(reinterpret_cast<int32_t *>(&_aidl_return));
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt32(reinterpret_cast<int32_t *>(&in_token));
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 13:
    {
      std::cout << "ITestService.RepeatLongEnum()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::LongEnum in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::LongEnum _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt64(reinterpret_cast<int64_t *>(&_aidl_return));
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt64(reinterpret_cast<int64_t *>(&in_token));
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 14:
    {
      std::cout << "ITestService.ReverseBoolean()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<bool> in_input;
      ::std::vector<bool> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<bool> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBoolVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readBoolVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readBoolVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 15:
    {
      std::cout << "ITestService.ReverseByte()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<uint8_t> in_input;
      ::std::vector<uint8_t> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<uint8_t> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readByteVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readByteVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readByteVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 16:
    {
      std::cout << "ITestService.ReverseChar()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<char16_t> in_input;
      ::std::vector<char16_t> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<char16_t> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readCharVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readCharVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readCharVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 17:
    {
      std::cout << "ITestService.ReverseInt()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<int32_t> in_input;
      ::std::vector<int32_t> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<int32_t> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt32Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt32Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readInt32Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 18:
    {
      std::cout << "ITestService.ReverseLong()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<int64_t> in_input;
      ::std::vector<int64_t> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<int64_t> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt64Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt64Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readInt64Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 19:
    {
      std::cout << "ITestService.ReverseFloat()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<float> in_input;
      ::std::vector<float> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<float> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readFloatVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readFloatVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readFloatVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 20:
    {
      std::cout << "ITestService.ReverseDouble()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<double> in_input;
      ::std::vector<double> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<double> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readDoubleVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readDoubleVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readDoubleVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 21:
    {
      std::cout << "ITestService.ReverseString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::String16> in_input;
      ::std::vector<::android::String16> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::String16> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readString16Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readString16Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 22:
    {
      std::cout << "ITestService.ReverseByteEnum()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::aidl::tests::ByteEnum> in_input;
      ::std::vector<::android::aidl::tests::ByteEnum> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::aidl::tests::ByteEnum> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readEnumVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readEnumVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 23:
    {
      std::cout << "ITestService.ReverseIntEnum()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::aidl::tests::IntEnum> in_input;
      ::std::vector<::android::aidl::tests::IntEnum> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::aidl::tests::IntEnum> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readEnumVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readEnumVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 24:
    {
      std::cout << "ITestService.ReverseLongEnum()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::aidl::tests::LongEnum> in_input;
      ::std::vector<::android::aidl::tests::LongEnum> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::aidl::tests::LongEnum> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readEnumVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readEnumVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 25:
    {
      std::cout << "ITestService.GetOtherTestService()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::String16 in_name;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::sp<::android::aidl::tests::INamedCallback> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinder(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16(&in_name);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument name from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    name: " << ::android::internal::ToString(in_name) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 26:
    {
      std::cout << "ITestService.SetOtherTestService()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::String16 in_name;
      ::android::sp<::android::aidl::tests::INamedCallback> in_service;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      bool _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBool(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16(&in_name);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument name from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readStrongBinder(&in_service);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument service from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    name: " << ::android::internal::ToString(in_name) << std::endl;
      std::cout << "    service: " << ::android::internal::ToString(in_service) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 27:
    {
      std::cout << "ITestService.VerifyName()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::sp<::android::aidl::tests::INamedCallback> in_service;
      ::android::String16 in_name;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      bool _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBool(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinder(&in_service);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument service from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readString16(&in_name);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument name from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    service: " << ::android::internal::ToString(in_service) << std::endl;
      std::cout << "    name: " << ::android::internal::ToString(in_name) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 28:
    {
      std::cout << "ITestService.GetInterfaceArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::String16> in_names;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinderVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16Vector(&in_names);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument names from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    names: " << ::android::internal::ToString(in_names) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 29:
    {
      std::cout << "ITestService.VerifyNamesWithInterfaceArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>> in_services;
      ::std::vector<::android::String16> in_names;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      bool _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBool(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_services);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument services from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readString16Vector(&in_names);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument names from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    services: " << ::android::internal::ToString(in_services) << std::endl;
      std::cout << "    names: " << ::android::internal::ToString(in_names) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 30:
    {
      std::cout << "ITestService.GetNullableInterfaceArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::android::String16>>> in_names;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinderVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16Vector(&in_names);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument names from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    names: " << ::android::internal::ToString(in_names) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 31:
    {
      std::cout << "ITestService.VerifyNamesWithNullableInterfaceArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>> in_services;
      ::std::optional<::std::vector<::std::optional<::android::String16>>> in_names;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      bool _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBool(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_services);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument services from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readString16Vector(&in_names);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument names from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    services: " << ::android::internal::ToString(in_services) << std::endl;
      std::cout << "    names: " << ::android::internal::ToString(in_names) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 32:
    {
      std::cout << "ITestService.GetInterfaceList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::android::String16>>> in_names;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinderVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16Vector(&in_names);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument names from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    names: " << ::android::internal::ToString(in_names) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 33:
    {
      std::cout << "ITestService.VerifyNamesWithInterfaceList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>> in_services;
      ::std::optional<::std::vector<::std::optional<::android::String16>>> in_names;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      bool _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readBool(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_services);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument services from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readString16Vector(&in_names);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument names from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    services: " << ::android::internal::ToString(in_services) << std::endl;
      std::cout << "    names: " << ::android::internal::ToString(in_names) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 34:
    {
      std::cout << "ITestService.ReverseStringList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::String16> in_input;
      ::std::vector<::android::String16> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::String16> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readString16Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readString16Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 35:
    {
      std::cout << "ITestService.RepeatParcelFileDescriptor()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::os::ParcelFileDescriptor in_read;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::os::ParcelFileDescriptor _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelable(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_read);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument read from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    read: " << ::android::internal::ToString(in_read) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 36:
    {
      std::cout << "ITestService.ReverseParcelFileDescriptorArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::os::ParcelFileDescriptor> in_input;
      ::std::vector<::android::os::ParcelFileDescriptor> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::os::ParcelFileDescriptor> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelableVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelableVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readParcelableVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 37:
    {
      std::cout << "ITestService.ThrowServiceException()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      int32_t in_code;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt32(&in_code);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument code from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    code: " << ::android::internal::ToString(in_code) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 38:
    {
      std::cout << "ITestService.RepeatNullableIntArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<int32_t>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<int32_t>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readInt32Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readInt32Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 39:
    {
      std::cout << "ITestService.RepeatNullableByteEnumArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readEnumVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 40:
    {
      std::cout << "ITestService.RepeatNullableIntEnumArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::aidl::tests::IntEnum>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::android::aidl::tests::IntEnum>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readEnumVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 41:
    {
      std::cout << "ITestService.RepeatNullableLongEnumArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::aidl::tests::LongEnum>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::android::aidl::tests::LongEnum>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readEnumVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 42:
    {
      std::cout << "ITestService.RepeatNullableString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::android::String16> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::android::String16> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readString16(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 43:
    {
      std::cout << "ITestService.RepeatNullableStringList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::android::String16>>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::std::optional<::android::String16>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readString16Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readString16Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 44:
    {
      std::cout << "ITestService.RepeatNullableParcelable()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::android::aidl::tests::ITestService::Empty> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::android::aidl::tests::ITestService::Empty> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelable(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 45:
    {
      std::cout << "ITestService.RepeatNullableParcelableArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelableVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelableVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 46:
    {
      std::cout << "ITestService.RepeatNullableParcelableList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelableVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelableVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 47:
    {
      std::cout << "ITestService.TakesAnIBinder()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::sp<::android::IBinder> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinder(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 48:
    {
      std::cout << "ITestService.TakesANullableIBinder()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::sp<::android::IBinder> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readNullableStrongBinder(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 49:
    {
      std::cout << "ITestService.TakesAnIBinderList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::sp<::android::IBinder>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 50:
    {
      std::cout << "ITestService.TakesANullableIBinderList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::sp<::android::IBinder>>> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 51:
    {
      std::cout << "ITestService.RepeatUtf8CppString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::string in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::string _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readUtf8FromUtf16(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readUtf8FromUtf16(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 52:
    {
      std::cout << "ITestService.RepeatNullableUtf8CppString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::string> in_token;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::string> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readUtf8FromUtf16(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readUtf8FromUtf16(&in_token);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument token from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    token: " << ::android::internal::ToString(in_token) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 53:
    {
      std::cout << "ITestService.ReverseUtf8CppString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::std::string> in_input;
      ::std::vector<::std::string> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::std::string> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readUtf8VectorFromUtf16Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readUtf8VectorFromUtf16Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readUtf8VectorFromUtf16Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 54:
    {
      std::cout << "ITestService.ReverseNullableUtf8CppString()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::std::string>>> in_input;
      ::std::optional<::std::vector<::std::optional<::std::string>>> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::std::optional<::std::string>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readUtf8VectorFromUtf16Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readUtf8VectorFromUtf16Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readUtf8VectorFromUtf16Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 55:
    {
      std::cout << "ITestService.ReverseUtf8CppStringList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::std::optional<::std::string>>> in_input;
      ::std::optional<::std::vector<::std::optional<::std::string>>> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::std::optional<::std::string>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readUtf8VectorFromUtf16Vector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readUtf8VectorFromUtf16Vector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readUtf8VectorFromUtf16Vector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 56:
    {
      std::cout << "ITestService.GetCallback()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      bool in_return_null;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::sp<::android::aidl::tests::INamedCallback> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readNullableStrongBinder(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readBool(&in_return_null);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument return_null from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    return_null: " << ::android::internal::ToString(in_return_null) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 57:
    {
      std::cout << "ITestService.FillOutStructuredParcelable()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::StructuredParcelable in_parcel;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_parcel);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument parcel from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    parcel: " << ::android::internal::ToString(in_parcel) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 58:
    {
      std::cout << "ITestService.RepeatExtendableParcelable()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::extension::ExtendableParcelable in_ep;
      ::android::aidl::tests::extension::ExtendableParcelable out_ep2;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_ep);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument ep from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readParcelable(&out_ep2);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument ep2 from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    ep: " << ::android::internal::ToString(in_ep) << std::endl;
      std::cout << "    ep2: " << ::android::internal::ToString(out_ep2) << std::endl;
      std::cout << "  return: void" << std::endl;
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 59:
    {
      std::cout << "ITestService.ReverseList()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::RecursiveList in_list;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::RecursiveList _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelable(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_list);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument list from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    list: " << ::android::internal::ToString(in_list) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 60:
    {
      std::cout << "ITestService.ReverseIBinderArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::sp<::android::IBinder>> in_input;
      ::std::vector<::android::sp<::android::IBinder>> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::sp<::android::IBinder>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinderVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 61:
    {
      std::cout << "ITestService.ReverseNullableIBinderArray()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::optional<::std::vector<::android::sp<::android::IBinder>>> in_input;
      ::std::optional<::std::vector<::android::sp<::android::IBinder>>> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::optional<::std::vector<::android::sp<::android::IBinder>>> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinderVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readStrongBinderVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 62:
    {
      std::cout << "ITestService.RepeatSimpleParcelable()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::SimpleParcelable in_input;
      ::android::aidl::tests::SimpleParcelable out_repeat;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::SimpleParcelable _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelable(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readParcelable(&out_repeat);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeat from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeat: " << ::android::internal::ToString(out_repeat) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 63:
    {
      std::cout << "ITestService.ReverseSimpleParcelables()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::aidl::tests::SimpleParcelable> in_input;
      ::std::vector<::android::aidl::tests::SimpleParcelable> out_repeated;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::aidl::tests::SimpleParcelable> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readParcelableVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelableVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
        _aidl_ret_status = _aidl_data.readParcelableVector(&out_repeated);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument repeated from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      std::cout << "    repeated: " << ::android::internal::ToString(out_repeated) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 64:
    {
      std::cout << "ITestService.GetOldNameInterface()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::sp<::android::aidl::tests::IOldName> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinder(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
      } while(false);
      std::cout << "  arguments: " << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 65:
    {
      std::cout << "ITestService.GetNewNameInterface()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::sp<::android::aidl::tests::INewName> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinder(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
      } while(false);
      std::cout << "  arguments: " << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 66:
    {
      std::cout << "ITestService.GetUnionTags()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::std::vector<::android::aidl::tests::Union> in_input;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::std::vector<::android::aidl::tests::Union::Tag> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readEnumVector(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelableVector(&in_input);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument input from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    input: " << ::android::internal::ToString(in_input) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 67:
    {
      std::cout << "ITestService.GetCppJavaTests()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::sp<::android::IBinder> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readNullableStrongBinder(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
      } while(false);
      std::cout << "  arguments: " << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 68:
    {
      std::cout << "ITestService.getBackendType()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::aidl::tests::BackendType _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readByte(reinterpret_cast<int8_t *>(&_aidl_return));
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
      } while(false);
      std::cout << "  arguments: " << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
    }
    break;
    case ::android::IBinder::FIRST_CALL_TRANSACTION + 69:
    {
      std::cout << "ITestService.GetCircular()" << std::endl;
      _aidl_ret_status = ::android::OK;
      if (!(_aidl_data.enforceInterface(android::String16("android.aidl.tests.ITestService")))) {
        _aidl_ret_status = ::android::BAD_TYPE;
        std::cout << "  Failure: Parcel interface does not match." << std::endl;
        break;
      }
      ::android::aidl::tests::CircularParcelable out_cp;
      ::android::binder::Status binderStatus;
      binderStatus.readFromParcel(_aidl_reply);
      ::android::sp<::android::aidl::tests::ICircular> _aidl_return;
      bool returnError = false;
      _aidl_ret_status = _aidl_reply.readStrongBinder(&_aidl_return);
      if (((_aidl_ret_status) != (android::NO_ERROR))) {
        std::cerr << "Failure: error in reading return value from Parcel." << std::endl;
        returnError = true;
      }
      do { // Single-pass loop to break if argument reading fails
        _aidl_ret_status = _aidl_data.readParcelable(&out_cp);
        if (((_aidl_ret_status) != (android::NO_ERROR))) {
          std::cerr << "Failure: error in reading argument cp from Parcel." << std::endl;
          break;
        }
      } while(false);
      if (!_aidl_data.enforceNoDataAvail().isOk()) {
        _aidl_ret_status = android::BAD_VALUE;
        std::cout << "  Failure: Parcel has too much data." << std::endl;
        break;
      }
      std::cout << "  arguments: " << std::endl;
      std::cout << "    cp: " << ::android::internal::ToString(out_cp) << std::endl;
      if (returnError) {
        std::cout << "  return: <error>" << std::endl;
      } else {std::cout << "  return: " << ::android::internal::ToString(_aidl_return) << std::endl;
      }
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
  Analyzer::installAnalyzer(std::make_unique<Analyzer>("android.aidl.tests.ITestService", "ITestService", &analyzeITestService));
}
