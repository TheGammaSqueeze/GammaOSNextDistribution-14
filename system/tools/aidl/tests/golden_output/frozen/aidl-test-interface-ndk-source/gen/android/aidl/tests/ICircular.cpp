/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ICircular.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICircular.aidl
 */
#include "aidl/android/aidl/tests/ICircular.h"

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
#include <aidl/android/aidl/tests/INamedCallback.h>
#include <aidl/android/aidl/tests/INewName.h>
#include <aidl/android/aidl/tests/IOldName.h>
#include <aidl/android/aidl/tests/ITestService.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
static binder_status_t _aidl_android_aidl_tests_ICircular_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnCircular> _aidl_impl = std::static_pointer_cast<BnCircular>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*GetTestService*/): {
      std::shared_ptr<::aidl::android::aidl::tests::ITestService> _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->GetTestService(&_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_tests_ICircular_clazz = ::ndk::ICInterface::defineClass(ICircular::descriptor, _aidl_android_aidl_tests_ICircular_onTransact);

BpCircular::BpCircular(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpCircular::~BpCircular() {}

::ndk::ScopedAStatus BpCircular::GetTestService(std::shared_ptr<::aidl::android::aidl::tests::ITestService>* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*GetTestService*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ICircular::getDefaultImpl()) {
    _aidl_status = ICircular::getDefaultImpl()->GetTestService(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
// Source for BnCircular
BnCircular::BnCircular() {}
BnCircular::~BnCircular() {}
::ndk::SpAIBinder BnCircular::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_tests_ICircular_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
// Source for ICircular
const char* ICircular::descriptor = "android.aidl.tests.ICircular";
ICircular::ICircular() {}
ICircular::~ICircular() {}


std::shared_ptr<ICircular> ICircular::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_tests_ICircular_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<BpCircular>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ICircular>(interface);
  }
  return ::ndk::SharedRefBase::make<BpCircular>(binder);
}

binder_status_t ICircular::writeToParcel(AParcel* parcel, const std::shared_ptr<ICircular>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ICircular::readFromParcel(const AParcel* parcel, std::shared_ptr<ICircular>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ICircular::fromBinder(binder);
  return STATUS_OK;
}
bool ICircular::setDefaultImpl(const std::shared_ptr<ICircular>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ICircular::default_impl);
  if (impl) {
    ICircular::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ICircular>& ICircular::getDefaultImpl() {
  return ICircular::default_impl;
}
std::shared_ptr<ICircular> ICircular::default_impl = nullptr;
::ndk::ScopedAStatus ICircularDefault::GetTestService(std::shared_ptr<::aidl::android::aidl::tests::ITestService>* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::SpAIBinder ICircularDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ICircularDefault::isRemote() {
  return false;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
