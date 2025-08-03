/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/staging/android/aidl/loggable/ILoggableInterface.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/ILoggableInterface.aidl
 */
#include "aidl/android/aidl/loggable/ILoggableInterface.h"

#include <android/binder_parcel_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/loggable/BnLoggableInterface.h>
#include <aidl/android/aidl/loggable/BpLoggableInterface.h>

namespace {
struct ScopedTrace {
  inline explicit ScopedTrace(const char* name) { ATrace_beginSection(name); }
  inline ~ScopedTrace() { ATrace_endSection(); }
};
}  // namespace

namespace aidl {
namespace android {
namespace aidl {
namespace loggable {
static binder_status_t _aidl_android_aidl_loggable_ILoggableInterface_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnLoggableInterface> _aidl_impl = std::static_pointer_cast<BnLoggableInterface>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*LogThis*/): {
      bool in_boolValue;
      std::vector<bool> in_boolArray;
      int8_t in_byteValue;
      std::vector<uint8_t> in_byteArray;
      char16_t in_charValue;
      std::vector<char16_t> in_charArray;
      int32_t in_intValue;
      std::vector<int32_t> in_intArray;
      int64_t in_longValue;
      std::vector<int64_t> in_longArray;
      float in_floatValue;
      std::vector<float> in_floatArray;
      double in_doubleValue;
      std::vector<double> in_doubleArray;
      std::string in_stringValue;
      std::vector<std::string> in_stringArray;
      std::vector<std::string> in_listValue;
      ::aidl::android::aidl::loggable::Data in_dataValue;
      ::ndk::SpAIBinder in_binderValue;
      ::ndk::ScopedFileDescriptor in_pfdValue;
      std::vector<::ndk::ScopedFileDescriptor> in_pfdArray;
      std::vector<std::string> _aidl_return;

      ScopedTrace _aidl_trace("AIDL::ndk::ILoggableInterface::LogThis::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_boolValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_boolArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_byteValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_byteArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_charValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_charArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_intValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_intArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_longValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_longArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_floatValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_floatArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_doubleValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_doubleArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_stringValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_stringArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_listValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_dataValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_in, &in_binderValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_in, &in_pfdValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_pfdArray);
      if (_aidl_ret_status != STATUS_OK) break;

      BnLoggableInterface::TransactionLog _transaction_log;
      if (BnLoggableInterface::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_boolValue", ::android::internal::ToString(in_boolValue));
        _transaction_log.input_args.emplace_back("in_boolArray", ::android::internal::ToString(in_boolArray));
        _transaction_log.input_args.emplace_back("in_byteValue", ::android::internal::ToString(in_byteValue));
        _transaction_log.input_args.emplace_back("in_byteArray", ::android::internal::ToString(in_byteArray));
        _transaction_log.input_args.emplace_back("in_charValue", ::android::internal::ToString(in_charValue));
        _transaction_log.input_args.emplace_back("in_charArray", ::android::internal::ToString(in_charArray));
        _transaction_log.input_args.emplace_back("in_intValue", ::android::internal::ToString(in_intValue));
        _transaction_log.input_args.emplace_back("in_intArray", ::android::internal::ToString(in_intArray));
        _transaction_log.input_args.emplace_back("in_longValue", ::android::internal::ToString(in_longValue));
        _transaction_log.input_args.emplace_back("in_longArray", ::android::internal::ToString(in_longArray));
        _transaction_log.input_args.emplace_back("in_floatValue", ::android::internal::ToString(in_floatValue));
        _transaction_log.input_args.emplace_back("in_floatArray", ::android::internal::ToString(in_floatArray));
        _transaction_log.input_args.emplace_back("in_doubleValue", ::android::internal::ToString(in_doubleValue));
        _transaction_log.input_args.emplace_back("in_doubleArray", ::android::internal::ToString(in_doubleArray));
        _transaction_log.input_args.emplace_back("in_stringValue", ::android::internal::ToString(in_stringValue));
        _transaction_log.input_args.emplace_back("in_stringArray", ::android::internal::ToString(in_stringArray));
        _transaction_log.input_args.emplace_back("in_listValue", ::android::internal::ToString(in_listValue));
        _transaction_log.input_args.emplace_back("in_dataValue", ::android::internal::ToString(in_dataValue));
        _transaction_log.input_args.emplace_back("in_binderValue", ::android::internal::ToString(in_binderValue));
        _transaction_log.input_args.emplace_back("in_pfdValue", ::android::internal::ToString(in_pfdValue));
        _transaction_log.input_args.emplace_back("in_pfdArray", ::android::internal::ToString(in_pfdArray));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->LogThis(in_boolValue, &in_boolArray, in_byteValue, &in_byteArray, in_charValue, &in_charArray, in_intValue, &in_intArray, in_longValue, &in_longArray, in_floatValue, &in_floatArray, in_doubleValue, &in_doubleArray, in_stringValue, &in_stringArray, &in_listValue, in_dataValue, in_binderValue, &in_pfdValue, &in_pfdArray, &_aidl_return);
      if (BnLoggableInterface::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.loggable.ILoggableInterface";
        _transaction_log.method_name = "LogThis";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        _transaction_log.output_args.emplace_back("in_boolArray", ::android::internal::ToString(in_boolArray));
        _transaction_log.output_args.emplace_back("in_byteArray", ::android::internal::ToString(in_byteArray));
        _transaction_log.output_args.emplace_back("in_charArray", ::android::internal::ToString(in_charArray));
        _transaction_log.output_args.emplace_back("in_intArray", ::android::internal::ToString(in_intArray));
        _transaction_log.output_args.emplace_back("in_longArray", ::android::internal::ToString(in_longArray));
        _transaction_log.output_args.emplace_back("in_floatArray", ::android::internal::ToString(in_floatArray));
        _transaction_log.output_args.emplace_back("in_doubleArray", ::android::internal::ToString(in_doubleArray));
        _transaction_log.output_args.emplace_back("in_stringArray", ::android::internal::ToString(in_stringArray));
        _transaction_log.output_args.emplace_back("in_listValue", ::android::internal::ToString(in_listValue));
        _transaction_log.output_args.emplace_back("in_pfdValue", ::android::internal::ToString(in_pfdValue));
        _transaction_log.output_args.emplace_back("in_pfdArray", ::android::internal::ToString(in_pfdArray));
        _transaction_log.result = ::android::internal::ToString(_aidl_return);
        BnLoggableInterface::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_boolArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_byteArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_charArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_intArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_longArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_floatArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_doubleArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_stringArray);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_listValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_out, in_pfdValue);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_pfdArray);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_loggable_ILoggableInterface_clazz = ::ndk::ICInterface::defineClass(ILoggableInterface::descriptor, _aidl_android_aidl_loggable_ILoggableInterface_onTransact);

BpLoggableInterface::BpLoggableInterface(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpLoggableInterface::~BpLoggableInterface() {}
std::function<void(const BpLoggableInterface::TransactionLog&)> BpLoggableInterface::logFunc;

::ndk::ScopedAStatus BpLoggableInterface::LogThis(bool in_boolValue, std::vector<bool>* in_boolArray, int8_t in_byteValue, std::vector<uint8_t>* in_byteArray, char16_t in_charValue, std::vector<char16_t>* in_charArray, int32_t in_intValue, std::vector<int32_t>* in_intArray, int64_t in_longValue, std::vector<int64_t>* in_longArray, float in_floatValue, std::vector<float>* in_floatArray, double in_doubleValue, std::vector<double>* in_doubleArray, const std::string& in_stringValue, std::vector<std::string>* in_stringArray, std::vector<std::string>* in_listValue, const ::aidl::android::aidl::loggable::Data& in_dataValue, const ::ndk::SpAIBinder& in_binderValue, ::ndk::ScopedFileDescriptor* in_pfdValue, std::vector<::ndk::ScopedFileDescriptor>* in_pfdArray, std::vector<std::string>* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  BpLoggableInterface::TransactionLog _transaction_log;
  if (BpLoggableInterface::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_boolValue", ::android::internal::ToString(in_boolValue));
    _transaction_log.input_args.emplace_back("in_boolArray", ::android::internal::ToString(*in_boolArray));
    _transaction_log.input_args.emplace_back("in_byteValue", ::android::internal::ToString(in_byteValue));
    _transaction_log.input_args.emplace_back("in_byteArray", ::android::internal::ToString(*in_byteArray));
    _transaction_log.input_args.emplace_back("in_charValue", ::android::internal::ToString(in_charValue));
    _transaction_log.input_args.emplace_back("in_charArray", ::android::internal::ToString(*in_charArray));
    _transaction_log.input_args.emplace_back("in_intValue", ::android::internal::ToString(in_intValue));
    _transaction_log.input_args.emplace_back("in_intArray", ::android::internal::ToString(*in_intArray));
    _transaction_log.input_args.emplace_back("in_longValue", ::android::internal::ToString(in_longValue));
    _transaction_log.input_args.emplace_back("in_longArray", ::android::internal::ToString(*in_longArray));
    _transaction_log.input_args.emplace_back("in_floatValue", ::android::internal::ToString(in_floatValue));
    _transaction_log.input_args.emplace_back("in_floatArray", ::android::internal::ToString(*in_floatArray));
    _transaction_log.input_args.emplace_back("in_doubleValue", ::android::internal::ToString(in_doubleValue));
    _transaction_log.input_args.emplace_back("in_doubleArray", ::android::internal::ToString(*in_doubleArray));
    _transaction_log.input_args.emplace_back("in_stringValue", ::android::internal::ToString(in_stringValue));
    _transaction_log.input_args.emplace_back("in_stringArray", ::android::internal::ToString(*in_stringArray));
    _transaction_log.input_args.emplace_back("in_listValue", ::android::internal::ToString(*in_listValue));
    _transaction_log.input_args.emplace_back("in_dataValue", ::android::internal::ToString(in_dataValue));
    _transaction_log.input_args.emplace_back("in_binderValue", ::android::internal::ToString(in_binderValue));
    _transaction_log.input_args.emplace_back("in_pfdValue", ::android::internal::ToString(*in_pfdValue));
    _transaction_log.input_args.emplace_back("in_pfdArray", ::android::internal::ToString(*in_pfdArray));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ILoggableInterface::LogThis::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_boolValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_boolArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_byteValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_byteArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_charValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_charArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_intValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_intArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_longValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_longArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_floatValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_floatArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_doubleValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_doubleArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_stringValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_stringArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_listValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_dataValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_in.get(), in_binderValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_in.get(), *in_pfdValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_pfdArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*LogThis*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ILoggableInterface::getDefaultImpl()) {
    _aidl_status = ILoggableInterface::getDefaultImpl()->LogThis(in_boolValue, in_boolArray, in_byteValue, in_byteArray, in_charValue, in_charArray, in_intValue, in_intArray, in_longValue, in_longArray, in_floatValue, in_floatArray, in_doubleValue, in_doubleArray, in_stringValue, in_stringArray, in_listValue, in_dataValue, in_binderValue, in_pfdValue, in_pfdArray, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_boolArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_byteArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_charArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_intArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_longArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_floatArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_doubleArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_stringArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_listValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_out.get(), in_pfdValue);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_pfdArray);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (BpLoggableInterface::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.loggable.ILoggableInterface";
    _transaction_log.method_name = "LogThis";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    _transaction_log.output_args.emplace_back("in_boolArray", ::android::internal::ToString(*in_boolArray));
    _transaction_log.output_args.emplace_back("in_byteArray", ::android::internal::ToString(*in_byteArray));
    _transaction_log.output_args.emplace_back("in_charArray", ::android::internal::ToString(*in_charArray));
    _transaction_log.output_args.emplace_back("in_intArray", ::android::internal::ToString(*in_intArray));
    _transaction_log.output_args.emplace_back("in_longArray", ::android::internal::ToString(*in_longArray));
    _transaction_log.output_args.emplace_back("in_floatArray", ::android::internal::ToString(*in_floatArray));
    _transaction_log.output_args.emplace_back("in_doubleArray", ::android::internal::ToString(*in_doubleArray));
    _transaction_log.output_args.emplace_back("in_stringArray", ::android::internal::ToString(*in_stringArray));
    _transaction_log.output_args.emplace_back("in_listValue", ::android::internal::ToString(*in_listValue));
    _transaction_log.output_args.emplace_back("in_pfdValue", ::android::internal::ToString(*in_pfdValue));
    _transaction_log.output_args.emplace_back("in_pfdArray", ::android::internal::ToString(*in_pfdArray));
    _transaction_log.result = ::android::internal::ToString(*_aidl_return);
    BpLoggableInterface::logFunc(_transaction_log);
  }
  return _aidl_status;
}
// Source for BnLoggableInterface
BnLoggableInterface::BnLoggableInterface() {}
BnLoggableInterface::~BnLoggableInterface() {}
std::function<void(const BnLoggableInterface::TransactionLog&)> BnLoggableInterface::logFunc;
::ndk::SpAIBinder BnLoggableInterface::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_loggable_ILoggableInterface_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
// Source for ILoggableInterface
const char* ILoggableInterface::descriptor = "android.aidl.loggable.ILoggableInterface";
ILoggableInterface::ILoggableInterface() {}
ILoggableInterface::~ILoggableInterface() {}


std::shared_ptr<ILoggableInterface> ILoggableInterface::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_loggable_ILoggableInterface_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<BpLoggableInterface>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ILoggableInterface>(interface);
  }
  return ::ndk::SharedRefBase::make<BpLoggableInterface>(binder);
}

binder_status_t ILoggableInterface::writeToParcel(AParcel* parcel, const std::shared_ptr<ILoggableInterface>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ILoggableInterface::readFromParcel(const AParcel* parcel, std::shared_ptr<ILoggableInterface>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ILoggableInterface::fromBinder(binder);
  return STATUS_OK;
}
bool ILoggableInterface::setDefaultImpl(const std::shared_ptr<ILoggableInterface>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ILoggableInterface::default_impl);
  if (impl) {
    ILoggableInterface::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ILoggableInterface>& ILoggableInterface::getDefaultImpl() {
  return ILoggableInterface::default_impl;
}
std::shared_ptr<ILoggableInterface> ILoggableInterface::default_impl = nullptr;
::ndk::ScopedAStatus ILoggableInterfaceDefault::LogThis(bool /*in_boolValue*/, std::vector<bool>* /*in_boolArray*/, int8_t /*in_byteValue*/, std::vector<uint8_t>* /*in_byteArray*/, char16_t /*in_charValue*/, std::vector<char16_t>* /*in_charArray*/, int32_t /*in_intValue*/, std::vector<int32_t>* /*in_intArray*/, int64_t /*in_longValue*/, std::vector<int64_t>* /*in_longArray*/, float /*in_floatValue*/, std::vector<float>* /*in_floatArray*/, double /*in_doubleValue*/, std::vector<double>* /*in_doubleArray*/, const std::string& /*in_stringValue*/, std::vector<std::string>* /*in_stringArray*/, std::vector<std::string>* /*in_listValue*/, const ::aidl::android::aidl::loggable::Data& /*in_dataValue*/, const ::ndk::SpAIBinder& /*in_binderValue*/, ::ndk::ScopedFileDescriptor* /*in_pfdValue*/, std::vector<::ndk::ScopedFileDescriptor>* /*in_pfdArray*/, std::vector<std::string>* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::SpAIBinder ILoggableInterfaceDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ILoggableInterfaceDefault::isRemote() {
  return false;
}
}  // namespace loggable
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace loggable {
static binder_status_t _aidl_android_aidl_loggable_ILoggableInterface_ISub_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<ILoggableInterface::BnSub> _aidl_impl = std::static_pointer_cast<ILoggableInterface::BnSub>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*Log*/): {
      int32_t in_value;

      ScopedTrace _aidl_trace("AIDL::ndk::ISub::Log::server");
      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_value);
      if (_aidl_ret_status != STATUS_OK) break;

      ILoggableInterface::BnSub::TransactionLog _transaction_log;
      if (ILoggableInterface::BnSub::logFunc != nullptr) {
        _transaction_log.input_args.emplace_back("in_value", ::android::internal::ToString(in_value));
      }
      auto _log_start = std::chrono::steady_clock::now();
      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->Log(in_value);
      if (ILoggableInterface::BnSub::logFunc != nullptr) {
        auto _log_end = std::chrono::steady_clock::now();
        _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
        _transaction_log.interface_name = "android.aidl.loggable.ILoggableInterface.ISub";
        _transaction_log.method_name = "Log";
        _transaction_log.stub_address = _aidl_impl.get();
        _transaction_log.proxy_address = nullptr;
        _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
        _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
        _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
        _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
        ILoggableInterface::BnSub::logFunc(_transaction_log);
      }
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_loggable_ILoggableInterface_ISub_clazz = ::ndk::ICInterface::defineClass(ILoggableInterface::ISub::descriptor, _aidl_android_aidl_loggable_ILoggableInterface_ISub_onTransact);

ILoggableInterface::BpSub::BpSub(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
ILoggableInterface::BpSub::~BpSub() {}
std::function<void(const ILoggableInterface::BpSub::TransactionLog&)> ILoggableInterface::BpSub::logFunc;

::ndk::ScopedAStatus ILoggableInterface::BpSub::Log(int32_t in_value) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  ILoggableInterface::BpSub::TransactionLog _transaction_log;
  if (ILoggableInterface::BpSub::logFunc != nullptr) {
    _transaction_log.input_args.emplace_back("in_value", ::android::internal::ToString(in_value));
  }
  auto _log_start = std::chrono::steady_clock::now();
  ScopedTrace _aidl_trace("AIDL::ndk::ISub::Log::client");
  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_value);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*Log*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISub::getDefaultImpl()) {
    _aidl_status = ISub::getDefaultImpl()->Log(in_value);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  if (ILoggableInterface::BpSub::logFunc != nullptr) {
    auto _log_end = std::chrono::steady_clock::now();
    _transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end - _log_start).count();
    _transaction_log.interface_name = "android.aidl.loggable.ILoggableInterface.ISub";
    _transaction_log.method_name = "Log";
    _transaction_log.stub_address = nullptr;
    _transaction_log.proxy_address = static_cast<const void*>(this);
    _transaction_log.exception_code = AStatus_getExceptionCode(_aidl_status.get());
    _transaction_log.exception_message = AStatus_getMessage(_aidl_status.get());
    _transaction_log.transaction_error = AStatus_getStatus(_aidl_status.get());
    _transaction_log.service_specific_error_code = AStatus_getServiceSpecificError(_aidl_status.get());
    ILoggableInterface::BpSub::logFunc(_transaction_log);
  }
  return _aidl_status;
}
// Source for BnSub
ILoggableInterface::BnSub::BnSub() {}
ILoggableInterface::BnSub::~BnSub() {}
std::function<void(const ILoggableInterface::BnSub::TransactionLog&)> ILoggableInterface::BnSub::logFunc;
::ndk::SpAIBinder ILoggableInterface::BnSub::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_loggable_ILoggableInterface_ISub_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
// Source for ISub
const char* ILoggableInterface::ISub::descriptor = "android.aidl.loggable.ILoggableInterface.ISub";
ILoggableInterface::ISub::ISub() {}
ILoggableInterface::ISub::~ISub() {}


std::shared_ptr<ILoggableInterface::ISub> ILoggableInterface::ISub::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_loggable_ILoggableInterface_ISub_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<ILoggableInterface::BpSub>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ISub>(interface);
  }
  return ::ndk::SharedRefBase::make<ILoggableInterface::BpSub>(binder);
}

binder_status_t ILoggableInterface::ISub::writeToParcel(AParcel* parcel, const std::shared_ptr<ISub>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ILoggableInterface::ISub::readFromParcel(const AParcel* parcel, std::shared_ptr<ISub>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ISub::fromBinder(binder);
  return STATUS_OK;
}
bool ILoggableInterface::ISub::setDefaultImpl(const std::shared_ptr<ISub>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ISub::default_impl);
  if (impl) {
    ISub::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ILoggableInterface::ISub>& ILoggableInterface::ISub::getDefaultImpl() {
  return ISub::default_impl;
}
std::shared_ptr<ILoggableInterface::ISub> ILoggableInterface::ISub::default_impl = nullptr;
::ndk::ScopedAStatus ILoggableInterface::ISubDefault::Log(int32_t /*in_value*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::SpAIBinder ILoggableInterface::ISubDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ILoggableInterface::ISubDefault::isRemote() {
  return false;
}
}  // namespace loggable
}  // namespace aidl
}  // namespace android
}  // namespace aidl
