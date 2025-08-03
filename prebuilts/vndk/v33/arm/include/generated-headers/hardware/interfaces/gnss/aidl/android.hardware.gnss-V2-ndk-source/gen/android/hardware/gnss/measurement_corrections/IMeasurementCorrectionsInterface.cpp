#include "aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsInterface.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/gnss/measurement_corrections/BnMeasurementCorrectionsCallback.h>
#include <aidl/android/hardware/gnss/measurement_corrections/BnMeasurementCorrectionsInterface.h>
#include <aidl/android/hardware/gnss/measurement_corrections/BpMeasurementCorrectionsCallback.h>
#include <aidl/android/hardware/gnss/measurement_corrections/BpMeasurementCorrectionsInterface.h>
#include <aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
static binder_status_t _aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsInterface_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnMeasurementCorrectionsInterface> _aidl_impl = std::static_pointer_cast<BnMeasurementCorrectionsInterface>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*setCorrections*/): {
      ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections in_corrections;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_corrections);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCorrections(in_corrections);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*setCallback*/): {
      std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback> in_callback;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_callback);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCallback(in_callback);
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

static AIBinder_Class* _g_aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsInterface_clazz = ::ndk::ICInterface::defineClass(IMeasurementCorrectionsInterface::descriptor, _aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsInterface_onTransact);

BpMeasurementCorrectionsInterface::BpMeasurementCorrectionsInterface(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpMeasurementCorrectionsInterface::~BpMeasurementCorrectionsInterface() {}

::ndk::ScopedAStatus BpMeasurementCorrectionsInterface::setCorrections(const ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections& in_corrections) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_corrections);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*setCorrections*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsInterface::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsInterface::getDefaultImpl()->setCorrections(in_corrections);
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
::ndk::ScopedAStatus BpMeasurementCorrectionsInterface::setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback>& in_callback) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_callback);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*setCallback*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsInterface::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsInterface::getDefaultImpl()->setCallback(in_callback);
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
::ndk::ScopedAStatus BpMeasurementCorrectionsInterface::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsInterface::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsInterface::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
::ndk::ScopedAStatus BpMeasurementCorrectionsInterface::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IMeasurementCorrectionsInterface::getDefaultImpl()) {
    _aidl_status = IMeasurementCorrectionsInterface::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
// Source for BnMeasurementCorrectionsInterface
BnMeasurementCorrectionsInterface::BnMeasurementCorrectionsInterface() {}
BnMeasurementCorrectionsInterface::~BnMeasurementCorrectionsInterface() {}
::ndk::SpAIBinder BnMeasurementCorrectionsInterface::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsInterface_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnMeasurementCorrectionsInterface::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = IMeasurementCorrectionsInterface::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnMeasurementCorrectionsInterface::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = IMeasurementCorrectionsInterface::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for IMeasurementCorrectionsInterface
const char* IMeasurementCorrectionsInterface::descriptor = "android.hardware.gnss.measurement_corrections.IMeasurementCorrectionsInterface";
IMeasurementCorrectionsInterface::IMeasurementCorrectionsInterface() {}
IMeasurementCorrectionsInterface::~IMeasurementCorrectionsInterface() {}


std::shared_ptr<IMeasurementCorrectionsInterface> IMeasurementCorrectionsInterface::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_gnss_measurement_corrections_IMeasurementCorrectionsInterface_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IMeasurementCorrectionsInterface>(interface);
  }
  return ::ndk::SharedRefBase::make<BpMeasurementCorrectionsInterface>(binder);
}

binder_status_t IMeasurementCorrectionsInterface::writeToParcel(AParcel* parcel, const std::shared_ptr<IMeasurementCorrectionsInterface>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t IMeasurementCorrectionsInterface::readFromParcel(const AParcel* parcel, std::shared_ptr<IMeasurementCorrectionsInterface>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IMeasurementCorrectionsInterface::fromBinder(binder);
  return STATUS_OK;
}
bool IMeasurementCorrectionsInterface::setDefaultImpl(const std::shared_ptr<IMeasurementCorrectionsInterface>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IMeasurementCorrectionsInterface::default_impl);
  if (impl) {
    IMeasurementCorrectionsInterface::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<IMeasurementCorrectionsInterface>& IMeasurementCorrectionsInterface::getDefaultImpl() {
  return IMeasurementCorrectionsInterface::default_impl;
}
std::shared_ptr<IMeasurementCorrectionsInterface> IMeasurementCorrectionsInterface::default_impl = nullptr;
::ndk::ScopedAStatus IMeasurementCorrectionsInterfaceDefault::setCorrections(const ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections& /*in_corrections*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IMeasurementCorrectionsInterfaceDefault::setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback>& /*in_callback*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IMeasurementCorrectionsInterfaceDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus IMeasurementCorrectionsInterfaceDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder IMeasurementCorrectionsInterfaceDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool IMeasurementCorrectionsInterfaceDefault::isRemote() {
  return false;
}
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
