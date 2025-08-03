/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/nested/INestedService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_ibinder.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/tests/nested/INestedService.h>
#include <aidl/android/aidl/tests/nested/ParcelableWithNested.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::aidl::tests::nested {
class ParcelableWithNested;
}  // namespace aidl::android::aidl::tests::nested
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace nested {
class INestedServiceDelegator;

class INestedService : public ::ndk::ICInterface {
public:
  typedef INestedServiceDelegator DefaultDelegator;
  static const char* descriptor;
  INestedService();
  virtual ~INestedService();

  class Result {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::aidl::android::aidl::tests::nested::ParcelableWithNested::Status status = ::aidl::android::aidl::tests::nested::ParcelableWithNested::Status::OK;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "Result{";
      _aidl_os << "status: " << ::android::internal::ToString(status);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };
  class ICallbackDelegator;

  class ICallback : public ::ndk::ICInterface {
  public:
    typedef ICallbackDelegator DefaultDelegator;
    static const char* descriptor;
    ICallback();
    virtual ~ICallback();

    static constexpr uint32_t TRANSACTION_done = FIRST_CALL_TRANSACTION + 0;

    static std::shared_ptr<ICallback> fromBinder(const ::ndk::SpAIBinder& binder);
    static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICallback>& instance);
    static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICallback>* instance);
    static bool setDefaultImpl(const std::shared_ptr<ICallback>& impl);
    static const std::shared_ptr<ICallback>& getDefaultImpl();
    virtual ::ndk::ScopedAStatus done(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status) = 0;
  private:
    static std::shared_ptr<ICallback> default_impl;
  };
  class ICallbackDefault : public ICallback {
  public:
    ::ndk::ScopedAStatus done(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status) override;
    ::ndk::SpAIBinder asBinder() override;
    bool isRemote() override;
  };
  class BpCallback : public ::ndk::BpCInterface<ICallback> {
  public:
    explicit BpCallback(const ::ndk::SpAIBinder& binder);
    virtual ~BpCallback();

    ::ndk::ScopedAStatus done(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status) override;
  };
  class BnCallback : public ::ndk::BnCInterface<ICallback> {
  public:
    BnCallback();
    virtual ~BnCallback();
  protected:
    ::ndk::SpAIBinder createBinder() override;
  private:
  };
  static constexpr uint32_t TRANSACTION_flipStatus = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_flipStatusWithCallback = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<INestedService> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INestedService>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INestedService>* instance);
  static bool setDefaultImpl(const std::shared_ptr<INestedService>& impl);
  static const std::shared_ptr<INestedService>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus flipStatus(const ::aidl::android::aidl::tests::nested::ParcelableWithNested& in_p, ::aidl::android::aidl::tests::nested::INestedService::Result* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus flipStatusWithCallback(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status, const std::shared_ptr<::aidl::android::aidl::tests::nested::INestedService::ICallback>& in_cb) = 0;
private:
  static std::shared_ptr<INestedService> default_impl;
};
class INestedServiceDefault : public INestedService {
public:
  ::ndk::ScopedAStatus flipStatus(const ::aidl::android::aidl::tests::nested::ParcelableWithNested& in_p, ::aidl::android::aidl::tests::nested::INestedService::Result* _aidl_return) override;
  ::ndk::ScopedAStatus flipStatusWithCallback(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status, const std::shared_ptr<::aidl::android::aidl::tests::nested::INestedService::ICallback>& in_cb) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
