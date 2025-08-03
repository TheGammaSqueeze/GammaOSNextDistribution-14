/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror --structured --version 2 --hash notfrozen -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test system/tools/aidl/tests/trunk_stable_test/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <android/aidl/test/trunk/BpTrunkStableTest.h>
namespace android {
namespace aidl {
namespace test {
namespace trunk {
DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_INTERFACE(TrunkStableTest, "android.aidl.test.trunk.ITrunkStableTest")
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/BpTrunkStableTest.h>
#include <android/aidl/test/trunk/BnTrunkStableTest.h>
#include <binder/Parcel.h>
#include <chrono>
#include <functional>

namespace android {
namespace aidl {
namespace test {
namespace trunk {

BpTrunkStableTest::BpTrunkStableTest(const ::android::sp<::android::IBinder>& _aidl_impl)
    : BpInterface<ITrunkStableTest>(_aidl_impl){
}

std::function<void(const BpTrunkStableTest::TransactionLog&)> BpTrunkStableTest::logFunc;

::android::binder::Status BpTrunkStableTest::repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatParcelable::cppClient");
  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeParcelable(input);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(BnTrunkStableTest::TRANSACTION_repeatParcelable, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) [[unlikely]] {
     return ITrunkStableTest::getDefaultImpl()->repeatParcelable(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readParcelable(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status BpTrunkStableTest::repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatEnum::cppClient");
  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeByte(static_cast<int8_t>(input));
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(BnTrunkStableTest::TRANSACTION_repeatEnum, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) [[unlikely]] {
     return ITrunkStableTest::getDefaultImpl()->repeatEnum(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readByte(reinterpret_cast<int8_t *>(_aidl_return));
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatEnum";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status BpTrunkStableTest::repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatUnion::cppClient");
  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeParcelable(input);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(BnTrunkStableTest::TRANSACTION_repeatUnion, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) [[unlikely]] {
     return ITrunkStableTest::getDefaultImpl()->repeatUnion(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readParcelable(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatUnion";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status BpTrunkStableTest::callMyCallback(const ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& cb) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::callMyCallback::cppClient");
  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("cb", ::android::internal::ToString(cb));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinder(cb);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(BnTrunkStableTest::TRANSACTION_callMyCallback, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) [[unlikely]] {
     return ITrunkStableTest::getDefaultImpl()->callMyCallback(cb);
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
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "callMyCallback";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status BpTrunkStableTest::repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatOtherParcelable::cppClient");
  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeParcelable(input);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(BnTrunkStableTest::TRANSACTION_repeatOtherParcelable, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) [[unlikely]] {
     return ITrunkStableTest::getDefaultImpl()->repeatOtherParcelable(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readParcelable(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatOtherParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}

int32_t BpTrunkStableTest::getInterfaceVersion() {
  if (cached_version_ == -1) {
    ::android::Parcel data;
    ::android::Parcel reply;
    data.writeInterfaceToken(getInterfaceDescriptor());
    ::android::status_t err = remote()->transact(BnTrunkStableTest::TRANSACTION_getInterfaceVersion, data, &reply);
    if (err == ::android::OK) {
      ::android::binder::Status _aidl_status;
      err = _aidl_status.readFromParcel(reply);
      if (err == ::android::OK && _aidl_status.isOk()) {
        cached_version_ = reply.readInt32();
      }
    }
  }
  return cached_version_;
}


std::string BpTrunkStableTest::getInterfaceHash() {
  std::lock_guard<std::mutex> lockGuard(cached_hash_mutex_);
  if (cached_hash_ == "-1") {
    ::android::Parcel data;
    ::android::Parcel reply;
    data.writeInterfaceToken(getInterfaceDescriptor());
    ::android::status_t err = remote()->transact(BnTrunkStableTest::TRANSACTION_getInterfaceHash, data, &reply);
    if (err == ::android::OK) {
      ::android::binder::Status _aidl_status;
      err = _aidl_status.readFromParcel(reply);
      if (err == ::android::OK && _aidl_status.isOk()) {
        reply.readUtf8FromUtf16(&cached_hash_);
      }
    }
  }
  return cached_hash_;
}

}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/BnTrunkStableTest.h>
#include <binder/Parcel.h>
#include <binder/Stability.h>
#include <chrono>
#include <functional>

namespace android {
namespace aidl {
namespace test {
namespace trunk {

BnTrunkStableTest::BnTrunkStableTest()
{
  ::android::internal::Stability::markCompilationUnit(this);
}

::android::status_t BnTrunkStableTest::onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  switch (_aidl_code) {
  case BnTrunkStableTest::TRANSACTION_repeatParcelable:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatParcelable::cppServer");
    _aidl_ret_status = _aidl_data.readParcelable(&in_input);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    BnTrunkStableTest::TransactionLog _transaction_log;
    if (BnTrunkStableTest::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatParcelable(in_input, &_aidl_return));
    if (BnTrunkStableTest::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
      _transaction_log.method_name = "repeatParcelable";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      BnTrunkStableTest::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeParcelable(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnTrunkStableTest::TRANSACTION_repeatEnum:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyEnum _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatEnum::cppServer");
    _aidl_ret_status = _aidl_data.readByte(reinterpret_cast<int8_t *>(&in_input));
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    BnTrunkStableTest::TransactionLog _transaction_log;
    if (BnTrunkStableTest::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatEnum(in_input, &_aidl_return));
    if (BnTrunkStableTest::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
      _transaction_log.method_name = "repeatEnum";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      BnTrunkStableTest::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeByte(static_cast<int8_t>(_aidl_return));
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnTrunkStableTest::TRANSACTION_repeatUnion:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyUnion in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyUnion _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatUnion::cppServer");
    _aidl_ret_status = _aidl_data.readParcelable(&in_input);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    BnTrunkStableTest::TransactionLog _transaction_log;
    if (BnTrunkStableTest::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatUnion(in_input, &_aidl_return));
    if (BnTrunkStableTest::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
      _transaction_log.method_name = "repeatUnion";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      BnTrunkStableTest::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeParcelable(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnTrunkStableTest::TRANSACTION_callMyCallback:
  {
    ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallback> in_cb;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::callMyCallback::cppServer");
    _aidl_ret_status = _aidl_data.readStrongBinder(&in_cb);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    BnTrunkStableTest::TransactionLog _transaction_log;
    if (BnTrunkStableTest::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_cb", ::android::internal::ToString(in_cb));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(callMyCallback(in_cb));
    if (BnTrunkStableTest::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
      _transaction_log.method_name = "callMyCallback";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      BnTrunkStableTest::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
  }
  break;
  case BnTrunkStableTest::TRANSACTION_repeatOtherParcelable:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::ITrunkStableTest::repeatOtherParcelable::cppServer");
    _aidl_ret_status = _aidl_data.readParcelable(&in_input);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    BnTrunkStableTest::TransactionLog _transaction_log;
    if (BnTrunkStableTest::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatOtherParcelable(in_input, &_aidl_return));
    if (BnTrunkStableTest::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
      _transaction_log.method_name = "repeatOtherParcelable";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      BnTrunkStableTest::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeParcelable(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnTrunkStableTest::TRANSACTION_getInterfaceVersion:
  {
    _aidl_data.checkInterface(this);
    _aidl_reply->writeNoException();
    _aidl_reply->writeInt32(ITrunkStableTest::VERSION);
  }
  break;
  case BnTrunkStableTest::TRANSACTION_getInterfaceHash:
  {
    _aidl_data.checkInterface(this);
    _aidl_reply->writeNoException();
    _aidl_reply->writeUtf8AsUtf16(ITrunkStableTest::HASH);
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

int32_t BnTrunkStableTest::getInterfaceVersion() {
  return ITrunkStableTest::VERSION;
}
std::string BnTrunkStableTest::getInterfaceHash() {
  return ITrunkStableTest::HASH;
}
std::function<void(const BnTrunkStableTest::TransactionLog&)> BnTrunkStableTest::logFunc;
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/ITrunkStableTest.h>

namespace android {
namespace aidl {
namespace test {
namespace trunk {
::android::status_t ITrunkStableTest::MyParcelable::readFromParcel(const ::android::Parcel* _aidl_parcel) {
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
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readInt32(&c);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
::android::status_t ITrunkStableTest::MyParcelable::writeToParcel(::android::Parcel* _aidl_parcel) const {
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
  _aidl_ret_status = _aidl_parcel->writeInt32(c);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  size_t _aidl_end_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->setDataPosition(_aidl_start_pos);
  _aidl_parcel->writeInt32(static_cast<int32_t>(_aidl_end_pos - _aidl_start_pos));
  _aidl_parcel->setDataPosition(_aidl_end_pos);
  return _aidl_ret_status;
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/ITrunkStableTest.h>

namespace android {
namespace aidl {
namespace test {
namespace trunk {
::android::status_t ITrunkStableTest::MyUnion::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_tag)) != ::android::OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case a: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<a>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<a>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case b: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<b>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<b>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case c: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<c>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<c>(std::move(_aidl_value));
    }
    return ::android::OK; }
  }
  return ::android::BAD_VALUE;
}
::android::status_t ITrunkStableTest::MyUnion::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = _aidl_parcel->writeInt32(static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != ::android::OK) return _aidl_ret_status;
  switch (getTag()) {
  case a: return _aidl_parcel->writeInt32(get<a>());
  case b: return _aidl_parcel->writeInt32(get<b>());
  case c: return _aidl_parcel->writeInt32(get<c>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <android/aidl/test/trunk/ITrunkStableTest.h>
namespace android {
namespace aidl {
namespace test {
namespace trunk {
DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_NESTED_INTERFACE(ITrunkStableTest, MyCallback, "android.aidl.test.trunk.ITrunkStableTest.IMyCallback")
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <binder/Parcel.h>
#include <chrono>
#include <functional>

namespace android {
namespace aidl {
namespace test {
namespace trunk {

ITrunkStableTest::BpMyCallback::BpMyCallback(const ::android::sp<::android::IBinder>& _aidl_impl)
    : BpInterface<IMyCallback>(_aidl_impl){
}

std::function<void(const ITrunkStableTest::BpMyCallback::TransactionLog&)> ITrunkStableTest::BpMyCallback::logFunc;

::android::binder::Status ITrunkStableTest::BpMyCallback::repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatParcelable::cppClient");
  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeParcelable(input);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(ITrunkStableTest::BnMyCallback::TRANSACTION_repeatParcelable, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) [[unlikely]] {
     return IMyCallback::getDefaultImpl()->repeatParcelable(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readParcelable(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status ITrunkStableTest::BpMyCallback::repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatEnum::cppClient");
  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeByte(static_cast<int8_t>(input));
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(ITrunkStableTest::BnMyCallback::TRANSACTION_repeatEnum, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) [[unlikely]] {
     return IMyCallback::getDefaultImpl()->repeatEnum(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readByte(reinterpret_cast<int8_t *>(_aidl_return));
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatEnum";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status ITrunkStableTest::BpMyCallback::repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatUnion::cppClient");
  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeParcelable(input);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(ITrunkStableTest::BnMyCallback::TRANSACTION_repeatUnion, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) [[unlikely]] {
     return IMyCallback::getDefaultImpl()->repeatUnion(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readParcelable(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatUnion";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}

::android::binder::Status ITrunkStableTest::BpMyCallback::repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatOtherParcelable::cppClient");
  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("input", ::android::internal::ToString(input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeParcelable(input);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(ITrunkStableTest::BnMyCallback::TRANSACTION_repeatOtherParcelable, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) [[unlikely]] {
     return IMyCallback::getDefaultImpl()->repeatOtherParcelable(input, _aidl_return);
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
  _aidl_ret_status = _aidl_reply.readParcelable(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatOtherParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = _aidl_status.exceptionCode();
    _transaction_log.exception_message = _aidl_status.exceptionMessage();
    _transaction_log.transaction_error = _aidl_status.transactionError();
    _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}

int32_t ITrunkStableTest::BpMyCallback::getInterfaceVersion() {
  if (cached_version_ == -1) {
    ::android::Parcel data;
    ::android::Parcel reply;
    data.writeInterfaceToken(getInterfaceDescriptor());
    ::android::status_t err = remote()->transact(ITrunkStableTest::BnMyCallback::TRANSACTION_getInterfaceVersion, data, &reply);
    if (err == ::android::OK) {
      ::android::binder::Status _aidl_status;
      err = _aidl_status.readFromParcel(reply);
      if (err == ::android::OK && _aidl_status.isOk()) {
        cached_version_ = reply.readInt32();
      }
    }
  }
  return cached_version_;
}


std::string ITrunkStableTest::BpMyCallback::getInterfaceHash() {
  std::lock_guard<std::mutex> lockGuard(cached_hash_mutex_);
  if (cached_hash_ == "-1") {
    ::android::Parcel data;
    ::android::Parcel reply;
    data.writeInterfaceToken(getInterfaceDescriptor());
    ::android::status_t err = remote()->transact(ITrunkStableTest::BnMyCallback::TRANSACTION_getInterfaceHash, data, &reply);
    if (err == ::android::OK) {
      ::android::binder::Status _aidl_status;
      err = _aidl_status.readFromParcel(reply);
      if (err == ::android::OK && _aidl_status.isOk()) {
        reply.readUtf8FromUtf16(&cached_hash_);
      }
    }
  }
  return cached_hash_;
}

}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <binder/Parcel.h>
#include <binder/Stability.h>
#include <chrono>
#include <functional>

namespace android {
namespace aidl {
namespace test {
namespace trunk {

ITrunkStableTest::BnMyCallback::BnMyCallback()
{
  ::android::internal::Stability::markCompilationUnit(this);
}

::android::status_t ITrunkStableTest::BnMyCallback::onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  switch (_aidl_code) {
  case BnMyCallback::TRANSACTION_repeatParcelable:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatParcelable::cppServer");
    _aidl_ret_status = _aidl_data.readParcelable(&in_input);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatParcelable(in_input, &_aidl_return));
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
      _transaction_log.method_name = "repeatParcelable";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeParcelable(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnMyCallback::TRANSACTION_repeatEnum:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyEnum _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatEnum::cppServer");
    _aidl_ret_status = _aidl_data.readByte(reinterpret_cast<int8_t *>(&in_input));
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatEnum(in_input, &_aidl_return));
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
      _transaction_log.method_name = "repeatEnum";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeByte(static_cast<int8_t>(_aidl_return));
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnMyCallback::TRANSACTION_repeatUnion:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyUnion in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyUnion _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatUnion::cppServer");
    _aidl_ret_status = _aidl_data.readParcelable(&in_input);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatUnion(in_input, &_aidl_return));
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
      _transaction_log.method_name = "repeatUnion";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeParcelable(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnMyCallback::TRANSACTION_repeatOtherParcelable:
  {
    ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable in_input;
    ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyCallback::repeatOtherParcelable::cppServer");
    _aidl_ret_status = _aidl_data.readParcelable(&in_input);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
    }
    auto _log_start = std::chrono::steady_clock::now();
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(repeatOtherParcelable(in_input, &_aidl_return));
    if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      auto _log_end = std::chrono::steady_clock::now();
      _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
      _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
      _transaction_log.method_name = "repeatOtherParcelable";
      _transaction_log.stub_address = static_cast<const void*>(this);
      _transaction_log.proxy_address = nullptr;
      _transaction_log.exception_code = _aidl_status.exceptionCode();
      _transaction_log.exception_message = _aidl_status.exceptionMessage();
      _transaction_log.transaction_error = _aidl_status.transactionError();
      _transaction_log.service_specific_error_code = _aidl_status.serviceSpecificErrorCode();
      _transaction_log.result = ::android::internal::ToString(_aidl_return);
      ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
    }
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeParcelable(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  case BnMyCallback::TRANSACTION_getInterfaceVersion:
  {
    _aidl_data.checkInterface(this);
    _aidl_reply->writeNoException();
    _aidl_reply->writeInt32(IMyCallback::VERSION);
  }
  break;
  case BnMyCallback::TRANSACTION_getInterfaceHash:
  {
    _aidl_data.checkInterface(this);
    _aidl_reply->writeNoException();
    _aidl_reply->writeUtf8AsUtf16(IMyCallback::HASH);
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

int32_t ITrunkStableTest::BnMyCallback::getInterfaceVersion() {
  return IMyCallback::VERSION;
}
std::string ITrunkStableTest::BnMyCallback::getInterfaceHash() {
  return IMyCallback::HASH;
}
std::function<void(const ITrunkStableTest::BnMyCallback::TransactionLog&)> ITrunkStableTest::BnMyCallback::logFunc;
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
#include <android/aidl/test/trunk/ITrunkStableTest.h>

namespace android {
namespace aidl {
namespace test {
namespace trunk {
::android::status_t ITrunkStableTest::MyOtherParcelable::readFromParcel(const ::android::Parcel* _aidl_parcel) {
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
::android::status_t ITrunkStableTest::MyOtherParcelable::writeToParcel(::android::Parcel* _aidl_parcel) const {
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
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
