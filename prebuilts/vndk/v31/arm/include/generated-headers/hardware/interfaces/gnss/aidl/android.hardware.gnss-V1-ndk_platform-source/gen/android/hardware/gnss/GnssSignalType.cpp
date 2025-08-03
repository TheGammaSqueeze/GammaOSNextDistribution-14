#include "aidl/android/hardware/gnss/GnssSignalType.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
const char* GnssSignalType::descriptor = "android.hardware.gnss.GnssSignalType";

const char* GnssSignalType::CODE_TYPE_A = "A";
const char* GnssSignalType::CODE_TYPE_B = "B";
const char* GnssSignalType::CODE_TYPE_C = "C";
const char* GnssSignalType::CODE_TYPE_D = "D";
const char* GnssSignalType::CODE_TYPE_I = "I";
const char* GnssSignalType::CODE_TYPE_L = "L";
const char* GnssSignalType::CODE_TYPE_M = "M";
const char* GnssSignalType::CODE_TYPE_N = "N";
const char* GnssSignalType::CODE_TYPE_P = "P";
const char* GnssSignalType::CODE_TYPE_Q = "Q";
const char* GnssSignalType::CODE_TYPE_S = "S";
const char* GnssSignalType::CODE_TYPE_W = "W";
const char* GnssSignalType::CODE_TYPE_X = "X";
const char* GnssSignalType::CODE_TYPE_Y = "Y";
const char* GnssSignalType::CODE_TYPE_Z = "Z";
const char* GnssSignalType::CODE_TYPE_UNKNOWN = "UNKNOWN";
binder_status_t GnssSignalType::readFromParcel(const AParcel* parcel) {
  int32_t _aidl_parcelable_size;
  int32_t _aidl_start_pos = AParcel_getDataPosition(parcel);
  binder_status_t _aidl_ret_status = AParcel_readInt32(parcel, &_aidl_parcelable_size);
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return STATUS_BAD_VALUE;
  if (_aidl_parcelable_size < 0) return STATUS_BAD_VALUE;
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = AParcel_readInt32(parcel, reinterpret_cast<int32_t*>(&constellation));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = AParcel_readDouble(parcel, &carrierFrequencyHz);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readString(parcel, &codeType);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
binder_status_t GnssSignalType::writeToParcel(AParcel* parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(parcel);
  _aidl_ret_status = AParcel_writeInt32(parcel, 0);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = AParcel_writeInt32(parcel, static_cast<int32_t>(constellation));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = AParcel_writeDouble(parcel, carrierFrequencyHz);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeString(parcel, codeType);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  size_t _aidl_end_pos = AParcel_getDataPosition(parcel);
  AParcel_setDataPosition(parcel, _aidl_start_pos);
  AParcel_writeInt32(parcel, _aidl_end_pos - _aidl_start_pos);
  AParcel_setDataPosition(parcel, _aidl_end_pos);
  return _aidl_ret_status;
}

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
