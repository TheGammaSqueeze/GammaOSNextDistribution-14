/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ListOfInterfaces.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ListOfInterfaces.aidl
 */
#include <android/aidl/tests/ListOfInterfaces.h>

namespace android {
namespace aidl {
namespace tests {
::android::status_t ListOfInterfaces::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  size_t _aidl_start_pos = _aidl_parcel->dataPosition();
  int32_t _aidl_parcelable_raw_size = 0;
  _aidl_ret_status = _aidl_parcel->readInt32(&_aidl_parcelable_raw_size);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcelable_raw_size < 4) return ::android::BAD_VALUE;
  size_t _aidl_parcelable_size = static_cast<size_t>(_aidl_parcelable_raw_size);
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return ::android::BAD_VALUE;
  _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
::android::status_t ListOfInterfaces::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = ::android::OK;
  size_t _aidl_start_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->writeInt32(0);
  size_t _aidl_end_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->setDataPosition(_aidl_start_pos);
  _aidl_parcel->writeInt32(static_cast<int32_t>(_aidl_end_pos - _aidl_start_pos));
  _aidl_parcel->setDataPosition(_aidl_end_pos);
  return _aidl_ret_status;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>
#include <android/aidl/tests/ListOfInterfaces.h>
namespace android {
namespace aidl {
namespace tests {
DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_NESTED_INTERFACE(ListOfInterfaces, EmptyInterface, "android.aidl.tests.ListOfInterfaces.IEmptyInterface")
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>
#include <android/aidl/tests/ListOfInterfaces.h>
#include <binder/Parcel.h>

namespace android {
namespace aidl {
namespace tests {

ListOfInterfaces::BpEmptyInterface::BpEmptyInterface(const ::android::sp<::android::IBinder>& _aidl_impl)
    : BpInterface<IEmptyInterface>(_aidl_impl){
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>
#include <binder/Parcel.h>
#include <binder/Stability.h>

namespace android {
namespace aidl {
namespace tests {

ListOfInterfaces::BnEmptyInterface::BnEmptyInterface()
{
  ::android::internal::Stability::markCompilationUnit(this);
}

::android::status_t ListOfInterfaces::BnEmptyInterface::onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  switch (_aidl_code) {
  default:
  {
    _aidl_ret_status = ::android::BBinder::onTransact(_aidl_code, _aidl_data, _aidl_reply, _aidl_flags);
  }
  break;
  }
  if (_aidl_ret_status == ::android::UNEXPECTED_NULL) {
    _aidl_ret_status = ::android::binder::Status::fromExceptionCode(::android::binder::Status::EX_NULL_POINTER).writeOverParcel(_aidl_reply);
  }
  return _aidl_ret_status;
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>
#include <android/aidl/tests/ListOfInterfaces.h>
namespace android {
namespace aidl {
namespace tests {
DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_NESTED_INTERFACE(ListOfInterfaces, MyInterface, "android.aidl.tests.ListOfInterfaces.IMyInterface")
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>
#include <android/aidl/tests/ListOfInterfaces.h>
#include <binder/Parcel.h>

namespace android {
namespace aidl {
namespace tests {

ListOfInterfaces::BpMyInterface::BpMyInterface(const ::android::sp<::android::IBinder>& _aidl_impl)
    : BpInterface<IMyInterface>(_aidl_impl){
}

::android::binder::Status ListOfInterfaces::BpMyInterface::methodWithInterfaces(const ::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& iface, const ::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& nullable_iface, const ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>& iface_list_in, ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* iface_list_out, ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* iface_list_inout, const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>& nullable_iface_list_in, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* nullable_iface_list_out, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* nullable_iface_list_inout, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* _aidl_return) {
  ::android::Parcel _aidl_data;
  _aidl_data.markForBinder(remoteStrong());
  ::android::Parcel _aidl_reply;
  ::android::status_t _aidl_ret_status = ::android::OK;
  ::android::binder::Status _aidl_status;
  ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyInterface::methodWithInterfaces::cppClient");
  _aidl_ret_status = _aidl_data.writeInterfaceToken(getInterfaceDescriptor());
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinder(iface);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinder(nullable_iface);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinderVector(iface_list_in);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinderVector(*iface_list_inout);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinderVector(nullable_iface_list_in);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_data.writeStrongBinderVector(*nullable_iface_list_inout);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = remote()->transact(ListOfInterfaces::BnMyInterface::TRANSACTION_methodWithInterfaces, _aidl_data, &_aidl_reply, 0);
  if (_aidl_ret_status == ::android::UNKNOWN_TRANSACTION && IMyInterface::getDefaultImpl()) [[unlikely]] {
     return IMyInterface::getDefaultImpl()->methodWithInterfaces(iface, nullable_iface, iface_list_in, iface_list_out, iface_list_inout, nullable_iface_list_in, nullable_iface_list_out, nullable_iface_list_inout, _aidl_return);
  }
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_status.readFromParcel(_aidl_reply);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  if (!_aidl_status.isOk()) {
    return _aidl_status;
  }
  _aidl_ret_status = _aidl_reply.readStrongBinderVector(_aidl_return);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_reply.readStrongBinderVector(iface_list_out);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_reply.readStrongBinderVector(iface_list_inout);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_reply.readStrongBinderVector(nullable_iface_list_out);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_ret_status = _aidl_reply.readStrongBinderVector(nullable_iface_list_inout);
  if (((_aidl_ret_status) != (::android::OK))) {
    goto _aidl_error;
  }
  _aidl_error:
  _aidl_status.setFromStatusT(_aidl_ret_status);
  return _aidl_status;
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>
#include <binder/Parcel.h>
#include <binder/Stability.h>

namespace android {
namespace aidl {
namespace tests {

ListOfInterfaces::BnMyInterface::BnMyInterface()
{
  ::android::internal::Stability::markCompilationUnit(this);
}

::android::status_t ListOfInterfaces::BnMyInterface::onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  switch (_aidl_code) {
  case BnMyInterface::TRANSACTION_methodWithInterfaces:
  {
    ::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface> in_iface;
    ::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface> in_nullable_iface;
    ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> in_iface_list_in;
    ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> out_iface_list_out;
    ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> in_iface_list_inout;
    ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> in_nullable_iface_list_in;
    ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> out_nullable_iface_list_out;
    ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> in_nullable_iface_list_inout;
    ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> _aidl_return;
    if (!(_aidl_data.checkInterface(this))) {
      _aidl_ret_status = ::android::BAD_TYPE;
      break;
    }
    ::android::binder::ScopedTrace _aidl_trace(ATRACE_TAG_AIDL, "AIDL::cpp::IMyInterface::methodWithInterfaces::cppServer");
    _aidl_ret_status = _aidl_data.readStrongBinder(&in_iface);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_data.readNullableStrongBinder(&in_nullable_iface);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_iface_list_in);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_iface_list_inout);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_nullable_iface_list_in);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_data.readStrongBinderVector(&in_nullable_iface_list_inout);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {
      _aidl_ret_status = st.writeToParcel(_aidl_reply);
      break;
    }
    ::android::binder::Status _aidl_status(methodWithInterfaces(in_iface, in_nullable_iface, in_iface_list_in, &out_iface_list_out, &in_iface_list_inout, in_nullable_iface_list_in, &out_nullable_iface_list_out, &in_nullable_iface_list_inout, &_aidl_return));
    _aidl_ret_status = _aidl_status.writeToParcel(_aidl_reply);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    if (!_aidl_status.isOk()) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeStrongBinderVector(_aidl_return);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeStrongBinderVector(out_iface_list_out);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeStrongBinderVector(in_iface_list_inout);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeStrongBinderVector(out_nullable_iface_list_out);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
    _aidl_ret_status = _aidl_reply->writeStrongBinderVector(in_nullable_iface_list_inout);
    if (((_aidl_ret_status) != (::android::OK))) {
      break;
    }
  }
  break;
  default:
  {
    _aidl_ret_status = ::android::BBinder::onTransact(_aidl_code, _aidl_data, _aidl_reply, _aidl_flags);
  }
  break;
  }
  if (_aidl_ret_status == ::android::UNEXPECTED_NULL) {
    _aidl_ret_status = ::android::binder::Status::fromExceptionCode(::android::binder::Status::EX_NULL_POINTER).writeOverParcel(_aidl_reply);
  }
  return _aidl_ret_status;
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>

namespace android {
namespace aidl {
namespace tests {
::android::status_t ListOfInterfaces::MyParcelable::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status = ::android::OK;
  size_t _aidl_start_pos = _aidl_parcel->dataPosition();
  int32_t _aidl_parcelable_raw_size = 0;
  _aidl_ret_status = _aidl_parcel->readInt32(&_aidl_parcelable_raw_size);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcelable_raw_size < 4) return ::android::BAD_VALUE;
  size_t _aidl_parcelable_size = static_cast<size_t>(_aidl_parcelable_raw_size);
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return ::android::BAD_VALUE;
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readStrongBinder(&iface);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readNullableStrongBinder(&nullable_iface);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readStrongBinderVector(&iface_list);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {
    _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->readStrongBinderVector(&nullable_iface_list);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
::android::status_t ListOfInterfaces::MyParcelable::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = ::android::OK;
  size_t _aidl_start_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->writeInt32(0);
  _aidl_ret_status = _aidl_parcel->writeStrongBinder(iface);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->writeStrongBinder(nullable_iface);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->writeStrongBinderVector(iface_list);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  _aidl_ret_status = _aidl_parcel->writeStrongBinderVector(nullable_iface_list);
  if (((_aidl_ret_status) != (::android::OK))) {
    return _aidl_ret_status;
  }
  size_t _aidl_end_pos = _aidl_parcel->dataPosition();
  _aidl_parcel->setDataPosition(_aidl_start_pos);
  _aidl_parcel->writeInt32(static_cast<int32_t>(_aidl_end_pos - _aidl_start_pos));
  _aidl_parcel->setDataPosition(_aidl_end_pos);
  return _aidl_ret_status;
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
#include <android/aidl/tests/ListOfInterfaces.h>

namespace android {
namespace aidl {
namespace tests {
::android::status_t ListOfInterfaces::MyUnion::readFromParcel(const ::android::Parcel* _aidl_parcel) {
  ::android::status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_tag)) != ::android::OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case iface: {
    ::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readStrongBinder(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>) {
      set<iface>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<iface>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case nullable_iface: {
    ::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readNullableStrongBinder(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>) {
      set<nullable_iface>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nullable_iface>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case iface_list: {
    ::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readStrongBinderVector(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>) {
      set<iface_list>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<iface_list>(std::move(_aidl_value));
    }
    return ::android::OK; }
  case nullable_iface_list: {
    ::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> _aidl_value;
    if ((_aidl_ret_status = _aidl_parcel->readStrongBinderVector(&_aidl_value)) != ::android::OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::std::optional<::std::vector<::android::sp<::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>>) {
      set<nullable_iface_list>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nullable_iface_list>(std::move(_aidl_value));
    }
    return ::android::OK; }
  }
  return ::android::BAD_VALUE;
}
::android::status_t ListOfInterfaces::MyUnion::writeToParcel(::android::Parcel* _aidl_parcel) const {
  ::android::status_t _aidl_ret_status = _aidl_parcel->writeInt32(static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != ::android::OK) return _aidl_ret_status;
  switch (getTag()) {
  case iface: return _aidl_parcel->writeStrongBinder(get<iface>());
  case nullable_iface: return _aidl_parcel->writeStrongBinder(get<nullable_iface>());
  case iface_list: return _aidl_parcel->writeStrongBinderVector(get<iface_list>());
  case nullable_iface_list: return _aidl_parcel->writeStrongBinderVector(get<nullable_iface_list>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
