/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --structured --version 2 --hash notfrozen -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-ndk-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-ndk-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test system/tools/aidl/tests/trunk_stable_test/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#include "aidl/android/aidl/test/trunk/ITrunkStableTest.h"

#include <android/binder_parcel_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/test/trunk/BnTrunkStableTest.h>
#include <aidl/android/aidl/test/trunk/BpTrunkStableTest.h>

namespace {
struct ScopedTrace {
  inline explicit ScopedTrace(const char* name) { ATrace_beginSection(name); }
  inline ~ScopedTrace() { ATrace_endSection(); }
};
}  // namespace

namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
static binder_status_t _aidl_android_aidl_test_trunk_ITrunkStableTest_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnTrunkStableTest> _aidl_impl = std::static_pointer_cast<BnTrunkStableTest>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*repeatParcelable*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatParcelable::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatParcelable(in_input, &_aidl_return);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "repeatParcelable";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*repeatEnum*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatEnum::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatEnum(in_input, &_aidl_return);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "repeatEnum";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 2 /*repeatUnion*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatUnion::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatUnion(in_input, &_aidl_return);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "repeatUnion";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 3 /*callMyCallback*/): {
      std::shared_ptr<::aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback> in_cb;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::callMyCallback::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_cb);
      if (_aidl_ret_status != STATUS_OK) break;

      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_cb", ::android::internal::ToString(in_cb));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->callMyCallback(in_cb);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "callMyCallback";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 4 /*repeatOtherParcelable*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatOtherParcelable::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatOtherParcelable(in_input, &_aidl_return);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "repeatOtherParcelable";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/): {
      int32_t _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::getInterfaceVersion::server");
      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceVersion(&_aidl_return);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "getInterfaceVersion";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/): {
      std::string _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::getInterfaceHash::server");
      BnTrunkStableTest::TransactionLog _transaction_log;
      if (BnTrunkStableTest::logFunc != nullptr) {
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceHash(&_aidl_return);
      if (BnTrunkStableTest::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
        _transaction_log.method_name = "getInterfaceHash";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnTrunkStableTest::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_test_trunk_ITrunkStableTest_clazz = ::ndk::ICInterface::defineClass(ITrunkStableTest::descriptor, _aidl_android_aidl_test_trunk_ITrunkStableTest_onTransact);

BpTrunkStableTest::BpTrunkStableTest(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpTrunkStableTest::~BpTrunkStableTest() {}
std::function<void(const BpTrunkStableTest::TransactionLog&)> BpTrunkStableTest::logFunc;

::ndk::ScopedAStatus BpTrunkStableTest::repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatParcelable::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*repeatParcelable*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->repeatParcelable(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus BpTrunkStableTest::repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatEnum::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*repeatEnum*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->repeatEnum(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatEnum";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus BpTrunkStableTest::repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatUnion::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 2 /*repeatUnion*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->repeatUnion(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatUnion";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus BpTrunkStableTest::callMyCallback(const std::shared_ptr<::aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& in_cb) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_cb", ::android::internal::ToString(in_cb));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::callMyCallback::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_cb);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 3 /*callMyCallback*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->callMyCallback(in_cb);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "callMyCallback";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus BpTrunkStableTest::repeatOtherParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::repeatOtherParcelable::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 4 /*repeatOtherParcelable*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->repeatOtherParcelable(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "repeatOtherParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus BpTrunkStableTest::getInterfaceVersion(int32_t* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  if (_aidl_cached_version != -1) {
    *_aidl_return = _aidl_cached_version;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::getInterfaceVersion::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->getInterfaceVersion(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_version = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "getInterfaceVersion";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus BpTrunkStableTest::getInterfaceHash(std::string* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  const std::lock_guard<std::mutex> lock(_aidl_cached_hash_mutex);
  if (_aidl_cached_hash != "-1") {
    *_aidl_return = _aidl_cached_hash;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpTrunkStableTest::TransactionLog _transaction_log;
  if (BpTrunkStableTest::logFunc != nullptr) {
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ITrunkStableTest::getInterfaceHash::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ITrunkStableTest::getDefaultImpl()) {
    _aidl_status = ITrunkStableTest::getDefaultImpl()->getInterfaceHash(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_hash = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpTrunkStableTest::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest";
    _transaction_log.method_name = "getInterfaceHash";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpTrunkStableTest::logFunc(_transaction_log);
  }
  return _aidl_status;
}
// Source for BnTrunkStableTest
BnTrunkStableTest::BnTrunkStableTest() {}
BnTrunkStableTest::~BnTrunkStableTest() {}
std::function<void(const BnTrunkStableTest::TransactionLog&)> BnTrunkStableTest::logFunc;
::ndk::SpAIBinder BnTrunkStableTest::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_test_trunk_ITrunkStableTest_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnTrunkStableTest::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = ITrunkStableTest::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnTrunkStableTest::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = ITrunkStableTest::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for ITrunkStableTest
const char* ITrunkStableTest::descriptor = "android.aidl.test.trunk.ITrunkStableTest";
ITrunkStableTest::ITrunkStableTest() {}
ITrunkStableTest::~ITrunkStableTest() {}


std::shared_ptr<ITrunkStableTest> ITrunkStableTest::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_test_trunk_ITrunkStableTest_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<BpTrunkStableTest>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ITrunkStableTest>(interface);
  }
  return ::ndk::SharedRefBase::make<BpTrunkStableTest>(binder);
}

binder_status_t ITrunkStableTest::writeToParcel(AParcel* parcel, const std::shared_ptr<ITrunkStableTest>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ITrunkStableTest::readFromParcel(const AParcel* parcel, std::shared_ptr<ITrunkStableTest>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ITrunkStableTest::fromBinder(binder);
  return STATUS_OK;
}
bool ITrunkStableTest::setDefaultImpl(const std::shared_ptr<ITrunkStableTest>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ITrunkStableTest::default_impl);
  if (impl) {
    ITrunkStableTest::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ITrunkStableTest>& ITrunkStableTest::getDefaultImpl() {
  return ITrunkStableTest::default_impl;
}
std::shared_ptr<ITrunkStableTest> ITrunkStableTest::default_impl = nullptr;
::ndk::ScopedAStatus ITrunkStableTestDefault::repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTestDefault::repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTestDefault::repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTestDefault::callMyCallback(const std::shared_ptr<::aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& /*in_cb*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTestDefault::repeatOtherParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTestDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ITrunkStableTestDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder ITrunkStableTestDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ITrunkStableTestDefault::isRemote() {
  return false;
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
const char* ITrunkStableTest::MyParcelable::descriptor = "android.aidl.test.trunk.ITrunkStableTest.MyParcelable";

binder_status_t ITrunkStableTest::MyParcelable::readFromParcel(const AParcel* _aidl_parcel) {
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
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &a);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &b);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &c);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
binder_status_t ITrunkStableTest::MyParcelable::writeToParcel(AParcel* _aidl_parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  _aidl_ret_status = AParcel_writeInt32(_aidl_parcel, 0);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, a);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, b);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, c);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  size_t _aidl_end_pos = AParcel_getDataPosition(_aidl_parcel);
  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos);
  AParcel_writeInt32(_aidl_parcel, _aidl_end_pos - _aidl_start_pos);
  AParcel_setDataPosition(_aidl_parcel, _aidl_end_pos);
  return _aidl_ret_status;
}

}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
const char* ITrunkStableTest::MyUnion::descriptor = "android.aidl.test.trunk.ITrunkStableTest.MyUnion";

binder_status_t ITrunkStableTest::MyUnion::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case a: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<a>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<a>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case b: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<b>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<b>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case c: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<c>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<c>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t ITrunkStableTest::MyUnion::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case a: return ::ndk::AParcel_writeData(_parcel, get<a>());
  case b: return ::ndk::AParcel_writeData(_parcel, get<b>());
  case c: return ::ndk::AParcel_writeData(_parcel, get<c>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
static binder_status_t _aidl_android_aidl_test_trunk_ITrunkStableTest_IMyCallback_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<ITrunkStableTest::BnMyCallback> _aidl_impl = std::static_pointer_cast<ITrunkStableTest::BnMyCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*repeatParcelable*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatParcelable::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatParcelable(in_input, &_aidl_return);
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
        _transaction_log.method_name = "repeatParcelable";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*repeatEnum*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatEnum::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatEnum(in_input, &_aidl_return);
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
        _transaction_log.method_name = "repeatEnum";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 2 /*repeatUnion*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatUnion::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatUnion(in_input, &_aidl_return);
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
        _transaction_log.method_name = "repeatUnion";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 3 /*repeatOtherParcelable*/): {
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable in_input;
      ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatOtherParcelable::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_input);
      if (_aidl_ret_status != STATUS_OK) break;

      ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->repeatOtherParcelable(in_input, &_aidl_return);
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
        _transaction_log.method_name = "repeatOtherParcelable";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/): {
      int32_t _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::getInterfaceVersion::server");
      ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceVersion(&_aidl_return);
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
        _transaction_log.method_name = "getInterfaceVersion";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/): {
      std::string _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::getInterfaceHash::server");
      ITrunkStableTest::BnMyCallback::TransactionLog _transaction_log;
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceHash(&_aidl_return);
      if (ITrunkStableTest::BnMyCallback::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
        _transaction_log.method_name = "getInterfaceHash";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        ITrunkStableTest::BnMyCallback::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_test_trunk_ITrunkStableTest_IMyCallback_clazz = ::ndk::ICInterface::defineClass(ITrunkStableTest::IMyCallback::descriptor, _aidl_android_aidl_test_trunk_ITrunkStableTest_IMyCallback_onTransact);

ITrunkStableTest::BpMyCallback::BpMyCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
ITrunkStableTest::BpMyCallback::~BpMyCallback() {}
std::function<void(const ITrunkStableTest::BpMyCallback::TransactionLog&)> ITrunkStableTest::BpMyCallback::logFunc;

::ndk::ScopedAStatus ITrunkStableTest::BpMyCallback::repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatParcelable::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*repeatParcelable*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) {
    _aidl_status = IMyCallback::getDefaultImpl()->repeatParcelable(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::BpMyCallback::repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatEnum::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*repeatEnum*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) {
    _aidl_status = IMyCallback::getDefaultImpl()->repeatEnum(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatEnum";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::BpMyCallback::repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatUnion::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 2 /*repeatUnion*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) {
    _aidl_status = IMyCallback::getDefaultImpl()->repeatUnion(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatUnion";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::BpMyCallback::repeatOtherParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_input", ::android::internal::ToString(in_input));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::repeatOtherParcelable::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_input);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 3 /*repeatOtherParcelable*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) {
    _aidl_status = IMyCallback::getDefaultImpl()->repeatOtherParcelable(in_input, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "repeatOtherParcelable";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::BpMyCallback::getInterfaceVersion(int32_t* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  if (_aidl_cached_version != -1) {
    *_aidl_return = _aidl_cached_version;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::getInterfaceVersion::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) {
    _aidl_status = IMyCallback::getDefaultImpl()->getInterfaceVersion(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_version = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "getInterfaceVersion";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::BpMyCallback::getInterfaceHash(std::string* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  const std::lock_guard<std::mutex> lock(_aidl_cached_hash_mutex);
  if (_aidl_cached_hash != "-1") {
    *_aidl_return = _aidl_cached_hash;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ITrunkStableTest::BpMyCallback::TransactionLog _transaction_log;
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::IMyCallback::getInterfaceHash::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyCallback::getDefaultImpl()) {
    _aidl_status = IMyCallback::getDefaultImpl()->getInterfaceHash(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_hash = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ITrunkStableTest::BpMyCallback::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
    _transaction_log.method_name = "getInterfaceHash";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    ITrunkStableTest::BpMyCallback::logFunc(_transaction_log);
  }
  return _aidl_status;
}
// Source for BnMyCallback
ITrunkStableTest::BnMyCallback::BnMyCallback() {}
ITrunkStableTest::BnMyCallback::~BnMyCallback() {}
std::function<void(const ITrunkStableTest::BnMyCallback::TransactionLog&)> ITrunkStableTest::BnMyCallback::logFunc;
::ndk::SpAIBinder ITrunkStableTest::BnMyCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_test_trunk_ITrunkStableTest_IMyCallback_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus ITrunkStableTest::BnMyCallback::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = IMyCallback::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ITrunkStableTest::BnMyCallback::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = IMyCallback::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for IMyCallback
const char* ITrunkStableTest::IMyCallback::descriptor = "android.aidl.test.trunk.ITrunkStableTest.IMyCallback";
ITrunkStableTest::IMyCallback::IMyCallback() {}
ITrunkStableTest::IMyCallback::~IMyCallback() {}


std::shared_ptr<ITrunkStableTest::IMyCallback> ITrunkStableTest::IMyCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_test_trunk_ITrunkStableTest_IMyCallback_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<ITrunkStableTest::BpMyCallback>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IMyCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<ITrunkStableTest::BpMyCallback>(binder);
}

binder_status_t ITrunkStableTest::IMyCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<IMyCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ITrunkStableTest::IMyCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<IMyCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IMyCallback::fromBinder(binder);
  return STATUS_OK;
}
bool ITrunkStableTest::IMyCallback::setDefaultImpl(const std::shared_ptr<IMyCallback>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IMyCallback::default_impl);
  if (impl) {
    IMyCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ITrunkStableTest::IMyCallback>& ITrunkStableTest::IMyCallback::getDefaultImpl() {
  return IMyCallback::default_impl;
}
std::shared_ptr<ITrunkStableTest::IMyCallback> ITrunkStableTest::IMyCallback::default_impl = nullptr;
::ndk::ScopedAStatus ITrunkStableTest::IMyCallbackDefault::repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::IMyCallbackDefault::repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::IMyCallbackDefault::repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::IMyCallbackDefault::repeatOtherParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& /*in_input*/, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ITrunkStableTest::IMyCallbackDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ITrunkStableTest::IMyCallbackDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder ITrunkStableTest::IMyCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ITrunkStableTest::IMyCallbackDefault::isRemote() {
  return false;
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
const char* ITrunkStableTest::MyOtherParcelable::descriptor = "android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable";

binder_status_t ITrunkStableTest::MyOtherParcelable::readFromParcel(const AParcel* _aidl_parcel) {
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
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &a);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &b);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
binder_status_t ITrunkStableTest::MyOtherParcelable::writeToParcel(AParcel* _aidl_parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  _aidl_ret_status = AParcel_writeInt32(_aidl_parcel, 0);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, a);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, b);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  size_t _aidl_end_pos = AParcel_getDataPosition(_aidl_parcel);
  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos);
  AParcel_writeInt32(_aidl_parcel, _aidl_end_pos - _aidl_start_pos);
  AParcel_setDataPosition(_aidl_parcel, _aidl_end_pos);
  return _aidl_ret_status;
}

}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
