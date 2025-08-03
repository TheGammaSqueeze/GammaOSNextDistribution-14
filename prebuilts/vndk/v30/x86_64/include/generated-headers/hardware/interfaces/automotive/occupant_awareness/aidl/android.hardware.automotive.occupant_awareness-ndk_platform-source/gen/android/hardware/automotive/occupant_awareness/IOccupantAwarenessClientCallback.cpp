#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/automotive/occupant_awareness/BpOccupantAwarenessClientCallback.h>
#include <aidl/android/hardware/automotive/occupant_awareness/BnOccupantAwarenessClientCallback.h>
#include <aidl/android/hardware/automotive/occupant_awareness/IOccupantAwarenessClientCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
static binder_status_t _aidl_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnOccupantAwarenessClientCallback> _aidl_impl = std::static_pointer_cast<BnOccupantAwarenessClientCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*onSystemStatusChanged*/): {
      int32_t in_detectionFlags;
      ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus in_status;

      _aidl_ret_status = AParcel_readInt32(_aidl_in, &in_detectionFlags);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = AParcel_readByte(_aidl_in, reinterpret_cast<int8_t*>(&in_status));
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onSystemStatusChanged(in_detectionFlags, in_status);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*onDetectionEvent*/): {
      ::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections in_detections;

      _aidl_ret_status = ::ndk::AParcel_readParcelable(_aidl_in, &in_detections);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDetectionEvent(in_detections);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/): {
      int32_t _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceVersion(&_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = AParcel_writeInt32(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/): {
      std::string _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceHash(&_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeString(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_clazz = ::ndk::ICInterface::defineClass(IOccupantAwarenessClientCallback::descriptor, _aidl_onTransact);

BpOccupantAwarenessClientCallback::BpOccupantAwarenessClientCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpOccupantAwarenessClientCallback::~BpOccupantAwarenessClientCallback() {}

::ndk::ScopedAStatus BpOccupantAwarenessClientCallback::onSystemStatusChanged(int32_t in_detectionFlags, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus in_status) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_writeInt32(_aidl_in.get(), in_detectionFlags);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_writeByte(_aidl_in.get(), static_cast<int8_t>(in_status));
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*onSystemStatusChanged*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IOccupantAwarenessClientCallback::getDefaultImpl()) {
    return IOccupantAwarenessClientCallback::getDefaultImpl()->onSystemStatusChanged(in_detectionFlags, in_status);
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  return _aidl_status;
}
::ndk::ScopedAStatus BpOccupantAwarenessClientCallback::onDetectionEvent(const ::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections& in_detections) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeParcelable(_aidl_in.get(), in_detections);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*onDetectionEvent*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IOccupantAwarenessClientCallback::getDefaultImpl()) {
    return IOccupantAwarenessClientCallback::getDefaultImpl()->onDetectionEvent(in_detections);
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  return _aidl_status;
}
::ndk::ScopedAStatus BpOccupantAwarenessClientCallback::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IOccupantAwarenessClientCallback::getDefaultImpl()) {
    return IOccupantAwarenessClientCallback::getDefaultImpl()->getInterfaceVersion(_aidl_return);
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) return _aidl_status;

  _aidl_ret_status = AParcel_readInt32(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_version = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  return _aidl_status;
}
::ndk::ScopedAStatus BpOccupantAwarenessClientCallback::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IOccupantAwarenessClientCallback::getDefaultImpl()) {
    return IOccupantAwarenessClientCallback::getDefaultImpl()->getInterfaceHash(_aidl_return);
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) return _aidl_status;

  _aidl_ret_status = ::ndk::AParcel_readString(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_hash = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  return _aidl_status;
}
// Source for BnOccupantAwarenessClientCallback
BnOccupantAwarenessClientCallback::BnOccupantAwarenessClientCallback() {}
BnOccupantAwarenessClientCallback::~BnOccupantAwarenessClientCallback() {}
::ndk::SpAIBinder BnOccupantAwarenessClientCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnOccupantAwarenessClientCallback::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = IOccupantAwarenessClientCallback::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnOccupantAwarenessClientCallback::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = IOccupantAwarenessClientCallback::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for IOccupantAwarenessClientCallback
const char* IOccupantAwarenessClientCallback::descriptor = "android.hardware.automotive.occupant_awareness.IOccupantAwarenessClientCallback";
IOccupantAwarenessClientCallback::IOccupantAwarenessClientCallback() {}
IOccupantAwarenessClientCallback::~IOccupantAwarenessClientCallback() {}


std::shared_ptr<IOccupantAwarenessClientCallback> IOccupantAwarenessClientCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IOccupantAwarenessClientCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<BpOccupantAwarenessClientCallback>(binder);
}

binder_status_t IOccupantAwarenessClientCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<IOccupantAwarenessClientCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t IOccupantAwarenessClientCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<IOccupantAwarenessClientCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IOccupantAwarenessClientCallback::fromBinder(binder);
  return STATUS_OK;
}
bool IOccupantAwarenessClientCallback::setDefaultImpl(std::shared_ptr<IOccupantAwarenessClientCallback> impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IOccupantAwarenessClientCallback::default_impl);
  if (impl) {
    IOccupantAwarenessClientCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<IOccupantAwarenessClientCallback>& IOccupantAwarenessClientCallback::getDefaultImpl() {
  return IOccupantAwarenessClientCallback::default_impl;
}
std::shared_ptr<IOccupantAwarenessClientCallback> IOccupantAwarenessClientCallback::default_impl = nullptr;
::ndk::ScopedAStatus IOccupantAwarenessClientCallbackDefault::onSystemStatusChanged(int32_t /*in_detectionFlags*/, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus /*in_status*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IOccupantAwarenessClientCallbackDefault::onDetectionEvent(const ::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections& /*in_detections*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IOccupantAwarenessClientCallbackDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus IOccupantAwarenessClientCallbackDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder IOccupantAwarenessClientCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool IOccupantAwarenessClientCallbackDefault::isRemote() {
  return false;
}
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
