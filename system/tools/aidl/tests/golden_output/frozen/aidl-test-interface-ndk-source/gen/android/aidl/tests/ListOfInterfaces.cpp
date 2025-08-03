/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ListOfInterfaces.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ListOfInterfaces.aidl
 */
#include "aidl/android/aidl/tests/ListOfInterfaces.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
const char* ListOfInterfaces::descriptor = "android.aidl.tests.ListOfInterfaces";

binder_status_t ListOfInterfaces::readFromParcel(const AParcel* _aidl_parcel) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  int32_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  int32_t _aidl_parcelable_size = 0;
  _aidl_ret_status = AParcel_readInt32(_aidl_parcel, &_aidl_parcelable_size);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (_aidl_parcelable_size < 4) return STATUS_BAD_VALUE;
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return STATUS_BAD_VALUE;
  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
binder_status_t ListOfInterfaces::writeToParcel(AParcel* _aidl_parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  _aidl_ret_status = AParcel_writeInt32(_aidl_parcel, 0);
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
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
static binder_status_t _aidl_android_aidl_tests_ListOfInterfaces_IEmptyInterface_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  (void)_aidl_binder;
  (void)_aidl_code;
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_tests_ListOfInterfaces_IEmptyInterface_clazz = ::ndk::ICInterface::defineClass(ListOfInterfaces::IEmptyInterface::descriptor, _aidl_android_aidl_tests_ListOfInterfaces_IEmptyInterface_onTransact);

ListOfInterfaces::BpEmptyInterface::BpEmptyInterface(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
ListOfInterfaces::BpEmptyInterface::~BpEmptyInterface() {}

// Source for BnEmptyInterface
ListOfInterfaces::BnEmptyInterface::BnEmptyInterface() {}
ListOfInterfaces::BnEmptyInterface::~BnEmptyInterface() {}
::ndk::SpAIBinder ListOfInterfaces::BnEmptyInterface::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_tests_ListOfInterfaces_IEmptyInterface_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
// Source for IEmptyInterface
const char* ListOfInterfaces::IEmptyInterface::descriptor = "android.aidl.tests.ListOfInterfaces.IEmptyInterface";
ListOfInterfaces::IEmptyInterface::IEmptyInterface() {}
ListOfInterfaces::IEmptyInterface::~IEmptyInterface() {}


std::shared_ptr<ListOfInterfaces::IEmptyInterface> ListOfInterfaces::IEmptyInterface::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_tests_ListOfInterfaces_IEmptyInterface_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<ListOfInterfaces::BpEmptyInterface>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IEmptyInterface>(interface);
  }
  return ::ndk::SharedRefBase::make<ListOfInterfaces::BpEmptyInterface>(binder);
}

binder_status_t ListOfInterfaces::IEmptyInterface::writeToParcel(AParcel* parcel, const std::shared_ptr<IEmptyInterface>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ListOfInterfaces::IEmptyInterface::readFromParcel(const AParcel* parcel, std::shared_ptr<IEmptyInterface>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IEmptyInterface::fromBinder(binder);
  return STATUS_OK;
}
bool ListOfInterfaces::IEmptyInterface::setDefaultImpl(const std::shared_ptr<IEmptyInterface>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IEmptyInterface::default_impl);
  if (impl) {
    IEmptyInterface::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ListOfInterfaces::IEmptyInterface>& ListOfInterfaces::IEmptyInterface::getDefaultImpl() {
  return IEmptyInterface::default_impl;
}
std::shared_ptr<ListOfInterfaces::IEmptyInterface> ListOfInterfaces::IEmptyInterface::default_impl = nullptr;
::ndk::SpAIBinder ListOfInterfaces::IEmptyInterfaceDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ListOfInterfaces::IEmptyInterfaceDefault::isRemote() {
  return false;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
static binder_status_t _aidl_android_aidl_tests_ListOfInterfaces_IMyInterface_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<ListOfInterfaces::BnMyInterface> _aidl_impl = std::static_pointer_cast<ListOfInterfaces::BnMyInterface>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*methodWithInterfaces*/): {
      std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface> in_iface;
      std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface> in_nullable_iface;
      std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> in_iface_list_in;
      std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> out_iface_list_out;
      std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> in_iface_list_inout;
      std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> in_nullable_iface_list_in;
      std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> out_nullable_iface_list_out;
      std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> in_nullable_iface_list_inout;
      std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> _aidl_return;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_iface);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_in, &in_nullable_iface);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_iface_list_in);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_iface_list_inout);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_in, &in_nullable_iface_list_in);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_in, &in_nullable_iface_list_inout);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->methodWithInterfaces(in_iface, in_nullable_iface, in_iface_list_in, &out_iface_list_out, &in_iface_list_inout, in_nullable_iface_list_in, &out_nullable_iface_list_out, &in_nullable_iface_list_inout, &_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, out_iface_list_out);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, in_iface_list_inout);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_out, out_nullable_iface_list_out);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_out, in_nullable_iface_list_inout);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_aidl_tests_ListOfInterfaces_IMyInterface_clazz = ::ndk::ICInterface::defineClass(ListOfInterfaces::IMyInterface::descriptor, _aidl_android_aidl_tests_ListOfInterfaces_IMyInterface_onTransact);

ListOfInterfaces::BpMyInterface::BpMyInterface(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
ListOfInterfaces::BpMyInterface::~BpMyInterface() {}

::ndk::ScopedAStatus ListOfInterfaces::BpMyInterface::methodWithInterfaces(const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_iface, const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_nullable_iface, const std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>& in_iface_list_in, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* out_iface_list_out, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* in_iface_list_inout, const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>& in_nullable_iface_list_in, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* out_nullable_iface_list_out, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* in_nullable_iface_list_inout, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_iface);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_in.get(), in_nullable_iface);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_iface_list_in);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), *in_iface_list_inout);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_in.get(), in_nullable_iface_list_in);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_in.get(), *in_nullable_iface_list_inout);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*methodWithInterfaces*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMyInterface::getDefaultImpl()) {
    _aidl_status = IMyInterface::getDefaultImpl()->methodWithInterfaces(in_iface, in_nullable_iface, in_iface_list_in, out_iface_list_out, in_iface_list_inout, in_nullable_iface_list_in, out_nullable_iface_list_out, in_nullable_iface_list_inout, _aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), out_iface_list_out);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), in_iface_list_inout);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_out.get(), out_nullable_iface_list_out);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_out.get(), in_nullable_iface_list_inout);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
// Source for BnMyInterface
ListOfInterfaces::BnMyInterface::BnMyInterface() {}
ListOfInterfaces::BnMyInterface::~BnMyInterface() {}
::ndk::SpAIBinder ListOfInterfaces::BnMyInterface::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_aidl_tests_ListOfInterfaces_IMyInterface_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markCompilationUnitStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
// Source for IMyInterface
const char* ListOfInterfaces::IMyInterface::descriptor = "android.aidl.tests.ListOfInterfaces.IMyInterface";
ListOfInterfaces::IMyInterface::IMyInterface() {}
ListOfInterfaces::IMyInterface::~IMyInterface() {}


std::shared_ptr<ListOfInterfaces::IMyInterface> ListOfInterfaces::IMyInterface::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_aidl_tests_ListOfInterfaces_IMyInterface_clazz)) {
    #if __ANDROID_API__ >= 31
    const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());
    if (originalClass == nullptr) return nullptr;
    if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {
      return ::ndk::SharedRefBase::make<ListOfInterfaces::BpMyInterface>(binder);
    }
    #endif
    return nullptr;
  }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IMyInterface>(interface);
  }
  return ::ndk::SharedRefBase::make<ListOfInterfaces::BpMyInterface>(binder);
}

binder_status_t ListOfInterfaces::IMyInterface::writeToParcel(AParcel* parcel, const std::shared_ptr<IMyInterface>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ListOfInterfaces::IMyInterface::readFromParcel(const AParcel* parcel, std::shared_ptr<IMyInterface>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IMyInterface::fromBinder(binder);
  return STATUS_OK;
}
bool ListOfInterfaces::IMyInterface::setDefaultImpl(const std::shared_ptr<IMyInterface>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IMyInterface::default_impl);
  if (impl) {
    IMyInterface::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ListOfInterfaces::IMyInterface>& ListOfInterfaces::IMyInterface::getDefaultImpl() {
  return IMyInterface::default_impl;
}
std::shared_ptr<ListOfInterfaces::IMyInterface> ListOfInterfaces::IMyInterface::default_impl = nullptr;
::ndk::ScopedAStatus ListOfInterfaces::IMyInterfaceDefault::methodWithInterfaces(const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& /*in_iface*/, const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& /*in_nullable_iface*/, const std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>& /*in_iface_list_in*/, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* /*out_iface_list_out*/, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* /*in_iface_list_inout*/, const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>& /*in_nullable_iface_list_in*/, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* /*out_nullable_iface_list_out*/, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* /*in_nullable_iface_list_inout*/, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* /*_aidl_return*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::SpAIBinder ListOfInterfaces::IMyInterfaceDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ListOfInterfaces::IMyInterfaceDefault::isRemote() {
  return false;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
const char* ListOfInterfaces::MyParcelable::descriptor = "android.aidl.tests.ListOfInterfaces.MyParcelable";

binder_status_t ListOfInterfaces::MyParcelable::readFromParcel(const AParcel* _aidl_parcel) {
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
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &iface);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_parcel, &nullable_iface);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_parcel, &iface_list);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readNullableData(_aidl_parcel, &nullable_iface_list);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
binder_status_t ListOfInterfaces::MyParcelable::writeToParcel(AParcel* _aidl_parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);
  _aidl_ret_status = AParcel_writeInt32(_aidl_parcel, 0);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, iface);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_parcel, nullable_iface);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_parcel, iface_list);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeNullableData(_aidl_parcel, nullable_iface_list);
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
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
const char* ListOfInterfaces::MyUnion::descriptor = "android.aidl.tests.ListOfInterfaces.MyUnion";

binder_status_t ListOfInterfaces::MyUnion::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case iface: {
    std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>) {
      set<iface>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<iface>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case nullable_iface: {
    std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readNullableData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>) {
      set<nullable_iface>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nullable_iface>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case iface_list: {
    std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>) {
      set<iface_list>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<iface_list>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case nullable_iface_list: {
    std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readNullableData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>>) {
      set<nullable_iface_list>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nullable_iface_list>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t ListOfInterfaces::MyUnion::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case iface: return ::ndk::AParcel_writeData(_parcel, get<iface>());
  case nullable_iface: return ::ndk::AParcel_writeNullableData(_parcel, get<nullable_iface>());
  case iface_list: return ::ndk::AParcel_writeData(_parcel, get<iface_list>());
  case nullable_iface_list: return ::ndk::AParcel_writeNullableData(_parcel, get<nullable_iface_list>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
