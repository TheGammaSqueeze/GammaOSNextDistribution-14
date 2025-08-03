#include "aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsCallback.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/gnss/measurement_corrections/BnMeasurementCorrectionsCallback.h>
#include <aidl/android/hardware/gnss/measurement_corrections/BpMeasurementCorrectionsCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
static binder_status_t _aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsCallback_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnMeasurementCorrectionsCallback> _aidl_impl = std::static_pointer_cast<BnMeasurementCorrectionsCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*setCapabilitiesCb*/): {
      int32_t in_capabilities;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_capabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCapabilitiesCb(in_capabilities);
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

static AIBinder_Class* _g_aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsCallback_clazz = ::ndk::ICInterface::defineClass(IMeasurementCorrectionsCallback::descriptor, _aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsCallback_onTransact);

BpMeasurementCorrectionsCallback::BpMeasurementCorrectionsCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpMeasurementCorrectionsCallback::~BpMeasurementCorrectionsCallback() {}

::ndk::ScopedAStatus BpMeasurementCorrectionsCallback::setCapabilitiesCb(int32_t in_capabilities) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_capabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*setCapabilitiesCb*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsCallback::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsCallback::getDefaultImpl()->setCapabilitiesCb(in_capabilities);
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
::ndk::ScopedAStatus BpMeasurementCorrectionsCallback::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsCallback::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsCallback::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
::ndk::ScopedAStatus BpMeasurementCorrectionsCallback::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsCallback::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsCallback::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
// Source for BnMeasurementCorrectionsCallback
BnMeasurementCorrectionsCallback::BnMeasurementCorrectionsCallback() {}
BnMeasurementCorrectionsCallback::~BnMeasurementCorrectionsCallback() {}
::ndk::SpAIBinder BnMeasurementCorrectionsCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsCallback_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnMeasurementCorrectionsCallback::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = IMeasurementCorrectionsCallback::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnMeasurementCorrectionsCallback::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = IMeasurementCorrectionsCallback::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for IMeasurementCorrectionsCallback
const char* IMeasurementCorrectionsCallback::descriptor = "android.hardware.gnss.measurement_corrections.IMeasurementCorrectionsCallback";
IMeasurementCorrectionsCallback::IMeasurementCorrectionsCallback() {}
IMeasurementCorrectionsCallback::~IMeasurementCorrectionsCallback() {}


std::shared_ptr<IMeasurementCorrectionsCallback> IMeasurementCorrectionsCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsCallback_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IMeasurementCorrectionsCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<BpMeasurementCorrectionsCallback>(binder);
}

binder_status_t IMeasurementCorrectionsCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<IMeasurementCorrectionsCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t IMeasurementCorrectionsCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<IMeasurementCorrectionsCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IMeasurementCorrectionsCallback::fromBinder(binder);
  return STATUS_OK;
}
bool IMeasurementCorrectionsCallback::setDefaultImpl(const std::shared_ptr<IMeasurementCorrectionsCallback>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IMeasurementCorrectionsCallback::default_impl);
  if (impl) {
    IMeasurementCorrectionsCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<IMeasurementCorrectionsCallback>& IMeasurementCorrectionsCallback::getDefaultImpl() {
  return IMeasurementCorrectionsCallback::default_impl;
}
std::shared_ptr<IMeasurementCorrectionsCallback> IMeasurementCorrectionsCallback::default_impl = nullptr;
::ndk::ScopedAStatus IMeasurementCorrectionsCallbackDefault::setCapabilitiesCb(int32_t /*in_capabilities*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IMeasurementCorrectionsCallbackDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus IMeasurementCorrectionsCallbackDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder IMeasurementCorrectionsCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool IMeasurementCorrectionsCallbackDefault::isRemote() {
  return false;
}
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
