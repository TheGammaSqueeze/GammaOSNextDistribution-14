/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/INamedCallback.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INamedCallback.aidl
 */
#include "aidl/android/aidl/tests/INamedCallback.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/aidl/tests/BnNamedCallback.h>
#include <aidl/android/aidl/tests/BpNamedCallback.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
static binder_status_t _aidl_android_aidl_tests_INamedCallback_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnNamedCallback> _aidl_impl = std::static_pointer_cast<BnNamedCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*GetName*/): {
      std::string _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->GetName(&_aidl_return);
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

static AIBinder_Class* _g_aidl_android_aidl_tests_INamedCallback_clazz = ::ndk::ICInterface::defineClass(INamedCallback::descriptor, _aidl_android_aidl_tests_INamedCallback_onTransact);

BpNamedCallback::BpNamedCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpNamedCallback::~BpNamedCallback() {}

::ndk::ScopedAStatus BpNamedCallback::GetName(std::string* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*GetName*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && INamedCallback::getDefaultImpl()) {
    _aidl_status = INamedCallback::getDefaultImpl()->GetName(_aidl_return);
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
  return _aidl_status;
}
// Source for BnNamedCallback
BnNamedCallback::BnNamedCallback() {}
BnNamedCallback::~BnNamedCallback() {}
::ndk::SpAIBinder BnNamedCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_tests_INamedCallback_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
// Source for INamedCallback
const char* INamedCallback::descriptor = "android.aidl.tests.INamedCallback";
INamedCallback::INamedCallback() {}
INamedCallback::~INamedCallback() {}


std::shared_ptr<INamedCallback> INamedCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_tests_INamedCallback_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<BpNamedCallback>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<INamedCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<BpNamedCallback>(binder);
}

binder_status_t INamedCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<INamedCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t INamedCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<INamedCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = INamedCallback::fromBinder(binder);
  return STATUS_OK;
}
bool INamedCallback::setDefaultImpl(const std::shared_ptr<INamedCallback>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!INamedCallback::default_impl);
  if (impl) {
    INamedCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<INamedCallback>& INamedCallback::getDefaultImpl() {
  return INamedCallback::default_impl;
}
std::shared_ptr<INamedCallback> INamedCallback::default_impl = nullptr;
::ndk::ScopedAStatus INamedCallbackDefault::GetName(std::string* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::SpAIBinder INamedCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool INamedCallbackDefault::isRemote() {
  return false;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
