#include "aidl/android/frameworks/cameraservice/service/ICameraServiceListener.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/frameworks/cameraservice/device/BnCameraDeviceCallback.h>
#include <aidl/android/frameworks/cameraservice/device/BnCameraDeviceUser.h>
#include <aidl/android/frameworks/cameraservice/device/BpCameraDeviceCallback.h>
#include <aidl/android/frameworks/cameraservice/device/BpCameraDeviceUser.h>
#include <aidl/android/frameworks/cameraservice/device/ICameraDeviceCallback.h>
#include <aidl/android/frameworks/cameraservice/device/ICameraDeviceUser.h>
#include <aidl/android/frameworks/cameraservice/service/BnCameraServiceListener.h>
#include <aidl/android/frameworks/cameraservice/service/BpCameraServiceListener.h>

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
static binder_status_t _aidl_android_frameworks_cameraservice_service_ICameraServiceListener_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnCameraServiceListener> _aidl_impl = std::static_pointer_cast<BnCameraServiceListener>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*onPhysicalCameraStatusChanged*/): {
      ::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status;
      std::string in_cameraId;
      std::string in_physicalCameraId;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_status);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_cameraId);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_physicalCameraId);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onPhysicalCameraStatusChanged(in_status, in_cameraId, in_physicalCameraId);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*onStatusChanged*/): {
      ::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status;
      std::string in_cameraId;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_status);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_cameraId);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onStatusChanged(in_status, in_cameraId);
      _aidl_ret_status = STATUS_OK;
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

static AIBinder_Class* _g_aidl_android_frameworks_cameraservice_service_ICameraServiceListener_clazz = ::ndk::ICInterface::defineClass(ICameraServiceListener::descriptor, _aidl_android_frameworks_cameraservice_service_ICameraServiceListener_onTransact);

BpCameraServiceListener::BpCameraServiceListener(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpCameraServiceListener::~BpCameraServiceListener() {}

::ndk::ScopedAStatus BpCameraServiceListener::onPhysicalCameraStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId, const std::string& in_physicalCameraId) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_status);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_cameraId);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_physicalCameraId);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*onPhysicalCameraStatusChanged*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ICameraServiceListener::getDefaultImpl()) {
    _aidl_status = ICameraServiceListener::getDefaultImpl()->onPhysicalCameraStatusChanged(in_status, in_cameraId, in_physicalCameraId);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpCameraServiceListener::onStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_status);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_cameraId);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*onStatusChanged*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ICameraServiceListener::getDefaultImpl()) {
    _aidl_status = ICameraServiceListener::getDefaultImpl()->onStatusChanged(in_status, in_cameraId);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpCameraServiceListener::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ICameraServiceListener::getDefaultImpl()) {
    _aidl_status = ICameraServiceListener::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
::ndk::ScopedAStatus BpCameraServiceListener::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ICameraServiceListener::getDefaultImpl()) {
    _aidl_status = ICameraServiceListener::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
// Source for BnCameraServiceListener
BnCameraServiceListener::BnCameraServiceListener() {}
BnCameraServiceListener::~BnCameraServiceListener() {}
::ndk::SpAIBinder BnCameraServiceListener::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_frameworks_cameraservice_service_ICameraServiceListener_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnCameraServiceListener::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = ICameraServiceListener::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnCameraServiceListener::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = ICameraServiceListener::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for ICameraServiceListener
const char* ICameraServiceListener::descriptor = "android.frameworks.cameraservice.service.ICameraServiceListener";
ICameraServiceListener::ICameraServiceListener() {}
ICameraServiceListener::~ICameraServiceListener() {}


std::shared_ptr<ICameraServiceListener> ICameraServiceListener::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_frameworks_cameraservice_service_ICameraServiceListener_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ICameraServiceListener>(interface);
  }
  return ::ndk::SharedRefBase::make<BpCameraServiceListener>(binder);
}

binder_status_t ICameraServiceListener::writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraServiceListener>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ICameraServiceListener::readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraServiceListener>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ICameraServiceListener::fromBinder(binder);
  return STATUS_OK;
}
bool ICameraServiceListener::setDefaultImpl(const std::shared_ptr<ICameraServiceListener>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ICameraServiceListener::default_impl);
  if (impl) {
    ICameraServiceListener::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ICameraServiceListener>& ICameraServiceListener::getDefaultImpl() {
  return ICameraServiceListener::default_impl;
}
std::shared_ptr<ICameraServiceListener> ICameraServiceListener::default_impl = nullptr;
::ndk::ScopedAStatus ICameraServiceListenerDefault::onPhysicalCameraStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus /*in_status*/, const std::string& /*in_cameraId*/, const std::string& /*in_physicalCameraId*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ICameraServiceListenerDefault::onStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus /*in_status*/, const std::string& /*in_cameraId*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ICameraServiceListenerDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ICameraServiceListenerDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder ICameraServiceListenerDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ICameraServiceListenerDefault::isRemote() {
  return false;
}
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
