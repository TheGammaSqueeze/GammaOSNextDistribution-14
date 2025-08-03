/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/nested/INestedService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
#pragma once

#include <android/aidl/tests/nested/INestedService.h>
#include <android/aidl/tests/nested/ParcelableWithNested.h>
#include <android/binder_to_string.h>
#include <binder/Delegate.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <tuple>
#include <utils/String16.h>
#include <utils/StrongPointer.h>

namespace android::aidl::tests::nested {
class ParcelableWithNested;
}  // namespace android::aidl::tests::nested
namespace android {
namespace aidl {
namespace tests {
namespace nested {
class INestedServiceDelegator;

class INestedService : public ::android::IInterface {
public:
  typedef INestedServiceDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(NestedService)
  class Result : public ::android::Parcelable {
  public:
    ::android::aidl::tests::nested::ParcelableWithNested::Status status = ::android::aidl::tests::nested::ParcelableWithNested::Status::OK;
    inline bool operator==(const Result& _rhs) const {
      return std::tie(status) == std::tie(_rhs.status);
    }
    inline bool operator<(const Result& _rhs) const {
      return std::tie(status) < std::tie(_rhs.status);
    }
    inline bool operator!=(const Result& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const Result& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const Result& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const Result& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.INestedService.Result");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "Result{";
      _aidl_os << "status: " << ::android::internal::ToString(status);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class Result
  class ICallbackDelegator;

  class ICallback : public ::android::IInterface {
  public:
    typedef ICallbackDelegator DefaultDelegator;
    DECLARE_META_INTERFACE(Callback)
    virtual ::android::binder::Status done(::android::aidl::tests::nested::ParcelableWithNested::Status status) = 0;
  };  // class ICallback

  class ICallbackDefault : public ICallback {
  public:
    ::android::IBinder* onAsBinder() override {
      return nullptr;
    }
    ::android::binder::Status done(::android::aidl::tests::nested::ParcelableWithNested::Status /*status*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
  };  // class ICallbackDefault
  class BpCallback : public ::android::BpInterface<ICallback> {
  public:
    explicit BpCallback(const ::android::sp<::android::IBinder>& _aidl_impl);
    virtual ~BpCallback() = default;
    ::android::binder::Status done(::android::aidl::tests::nested::ParcelableWithNested::Status status) override;
  };  // class BpCallback
  class BnCallback : public ::android::BnInterface<ICallback> {
  public:
    static constexpr uint32_t TRANSACTION_done = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
    explicit BnCallback();
    ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
  };  // class BnCallback

  class ICallbackDelegator : public BnCallback {
  public:
    explicit ICallbackDelegator(const ::android::sp<ICallback> &impl) : _aidl_delegate(impl) {}

    ::android::sp<ICallback> getImpl() { return _aidl_delegate; }
    ::android::binder::Status done(::android::aidl::tests::nested::ParcelableWithNested::Status status) override {
      return _aidl_delegate->done(status);
    }
  private:
    ::android::sp<ICallback> _aidl_delegate;
  };  // class ICallbackDelegator
  virtual ::android::binder::Status flipStatus(const ::android::aidl::tests::nested::ParcelableWithNested& p, ::android::aidl::tests::nested::INestedService::Result* _aidl_return) = 0;
  virtual ::android::binder::Status flipStatusWithCallback(::android::aidl::tests::nested::ParcelableWithNested::Status status, const ::android::sp<::android::aidl::tests::nested::INestedService::ICallback>& cb) = 0;
};  // class INestedService

class INestedServiceDefault : public INestedService {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status flipStatus(const ::android::aidl::tests::nested::ParcelableWithNested& /*p*/, ::android::aidl::tests::nested::INestedService::Result* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status flipStatusWithCallback(::android::aidl::tests::nested::ParcelableWithNested::Status /*status*/, const ::android::sp<::android::aidl::tests::nested::INestedService::ICallback>& /*cb*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class INestedServiceDefault
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
