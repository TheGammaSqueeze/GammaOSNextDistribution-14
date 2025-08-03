#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHwGlobalCallback.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/soundtrigger3/BnSoundTriggerHwGlobalCallback.h>
#include <aidl/android/hardware/soundtrigger3/BpSoundTriggerHwGlobalCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace soundtrigger3 {
static binder_status_t _aidl_android_hardware_soundtrigger3_ISoundTriggerHwGlobalCallback_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnSoundTriggerHwGlobalCallback> _aidl_impl = std::static_pointer_cast<BnSoundTriggerHwGlobalCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*onResourcesAvailable*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onResourcesAvailable();
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/): {
      int32_t _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceVersion(&_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/): {
      std::string _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceHash(&_aidl_return);
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

static AIBinder_Class* _g_aidl_android_hardware_soundtrigger3_ISoundTriggerHwGlobalCallback_clazz = ::ndk::ICInterface::defineClass(ISoundTriggerHwGlobalCallback::descriptor, _aidl_android_hardware_soundtrigger3_ISoundTriggerHwGlobalCallback_onTransact);

BpSoundTriggerHwGlobalCallback::BpSoundTriggerHwGlobalCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpSoundTriggerHwGlobalCallback::~BpSoundTriggerHwGlobalCallback() {}

::ndk::ScopedAStatus BpSoundTriggerHwGlobalCallback::onResourcesAvailable() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*onResourcesAvailable*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISoundTriggerHwGlobalCallback::getDefaultImpl()) {
    _aidl_status = ISoundTriggerHwGlobalCallback::getDefaultImpl()->onResourcesAvailable();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSoundTriggerHwGlobalCallback::getInterfaceVersion(int32_t* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  if (_aidl_cached_version != -1) {
    *_aidl_return = _aidl_cached_version;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISoundTriggerHwGlobalCallback::getDefaultImpl()) {
    _aidl_status = ISoundTriggerHwGlobalCallback::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
  return _aidl_status;
}
::ndk::ScopedAStatus BpSoundTriggerHwGlobalCallback::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISoundTriggerHwGlobalCallback::getDefaultImpl()) {
    _aidl_status = ISoundTriggerHwGlobalCallback::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
  return _aidl_status;
}
// Source for BnSoundTriggerHwGlobalCallback
BnSoundTriggerHwGlobalCallback::BnSoundTriggerHwGlobalCallback() {}
BnSoundTriggerHwGlobalCallback::~BnSoundTriggerHwGlobalCallback() {}
::ndk::SpAIBinder BnSoundTriggerHwGlobalCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_soundtrigger3_ISoundTriggerHwGlobalCallback_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnSoundTriggerHwGlobalCallback::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = ISoundTriggerHwGlobalCallback::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnSoundTriggerHwGlobalCallback::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = ISoundTriggerHwGlobalCallback::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for ISoundTriggerHwGlobalCallback
const char* ISoundTriggerHwGlobalCallback::descriptor = "android.hardware.soundtrigger3.ISoundTriggerHwGlobalCallback";
ISoundTriggerHwGlobalCallback::ISoundTriggerHwGlobalCallback() {}
ISoundTriggerHwGlobalCallback::~ISoundTriggerHwGlobalCallback() {}


std::shared_ptr<ISoundTriggerHwGlobalCallback> ISoundTriggerHwGlobalCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_soundtrigger3_ISoundTriggerHwGlobalCallback_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ISoundTriggerHwGlobalCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<BpSoundTriggerHwGlobalCallback>(binder);
}

binder_status_t ISoundTriggerHwGlobalCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<ISoundTriggerHwGlobalCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ISoundTriggerHwGlobalCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<ISoundTriggerHwGlobalCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ISoundTriggerHwGlobalCallback::fromBinder(binder);
  return STATUS_OK;
}
bool ISoundTriggerHwGlobalCallback::setDefaultImpl(const std::shared_ptr<ISoundTriggerHwGlobalCallback>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ISoundTriggerHwGlobalCallback::default_impl);
  if (impl) {
    ISoundTriggerHwGlobalCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ISoundTriggerHwGlobalCallback>& ISoundTriggerHwGlobalCallback::getDefaultImpl() {
  return ISoundTriggerHwGlobalCallback::default_impl;
}
std::shared_ptr<ISoundTriggerHwGlobalCallback> ISoundTriggerHwGlobalCallback::default_impl = nullptr;
::ndk::ScopedAStatus ISoundTriggerHwGlobalCallbackDefault::onResourcesAvailable() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISoundTriggerHwGlobalCallbackDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ISoundTriggerHwGlobalCallbackDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder ISoundTriggerHwGlobalCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ISoundTriggerHwGlobalCallbackDefault::isRemote() {
  return false;
}
}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
