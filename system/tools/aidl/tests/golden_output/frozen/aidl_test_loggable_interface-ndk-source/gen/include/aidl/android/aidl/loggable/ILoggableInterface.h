/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/staging/android/aidl/loggable/ILoggableInterface.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/ILoggableInterface.aidl
 */
#pragma once

#include <chrono>
#include <cstdint>
#include <functional>
#include <memory>
#include <optional>
#include <sstream>
#include <string>
#include <vector>
#include <android/binder_ibinder.h>
#include <android/binder_interface_utils.h>
#include <android/trace.h>
#include <aidl/android/aidl/loggable/Data.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::aidl::loggable {
class Data;
}  // namespace aidl::android::aidl::loggable
namespace aidl {
namespace android {
namespace aidl {
namespace loggable {
class ILoggableInterfaceDelegator;

class ILoggableInterface : public ::ndk::ICInterface {
public:
  typedef ILoggableInterfaceDelegator DefaultDelegator;
  static const char* descriptor;
  ILoggableInterface();
  virtual ~ILoggableInterface();

  class ISubDelegator;

  class ISub : public ::ndk::ICInterface {
  public:
    typedef ISubDelegator DefaultDelegator;
    static const char* descriptor;
    ISub();
    virtual ~ISub();

    static constexpr uint32_t TRANSACTION_Log = FIRST_CALL_TRANSACTION + 0;

    static std::shared_ptr<ISub> fromBinder(const ::ndk::SpAIBinder& binder);
    static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISub>& instance);
    static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISub>* instance);
    static bool setDefaultImpl(const std::shared_ptr<ISub>& impl);
    static const std::shared_ptr<ISub>& getDefaultImpl();
    virtual ::ndk::ScopedAStatus Log(int32_t in_value) = 0;
  private:
    static std::shared_ptr<ISub> default_impl;
  };
  class ISubDefault : public ISub {
  public:
    ::ndk::ScopedAStatus Log(int32_t in_value) override;
    ::ndk::SpAIBinder asBinder() override;
    bool isRemote() override;
  };
  class BpSub : public ::ndk::BpCInterface<ISub> {
  public:
    explicit BpSub(const ::ndk::SpAIBinder& binder);
    virtual ~BpSub();

    ::ndk::ScopedAStatus Log(int32_t in_value) override;
    struct TransactionLog {
      double duration_ms;
      std::string interface_name;
      std::string method_name;
      const void* proxy_address;
      const void* stub_address;
      std::vector<std::pair<std::string, std::string>> input_args;
      std::vector<std::pair<std::string, std::string>> output_args;
      std::string result;
      std::string exception_message;
      int32_t exception_code;
      int32_t transaction_error;
      int32_t service_specific_error_code;
    };
    static std::function<void(const TransactionLog&)> logFunc;
  };
  class BnSub : public ::ndk::BnCInterface<ISub> {
  public:
    BnSub();
    virtual ~BnSub();
    struct TransactionLog {
      double duration_ms;
      std::string interface_name;
      std::string method_name;
      const void* proxy_address;
      const void* stub_address;
      std::vector<std::pair<std::string, std::string>> input_args;
      std::vector<std::pair<std::string, std::string>> output_args;
      std::string result;
      std::string exception_message;
      int32_t exception_code;
      int32_t transaction_error;
      int32_t service_specific_error_code;
    };
    static std::function<void(const TransactionLog&)> logFunc;
  protected:
    ::ndk::SpAIBinder createBinder() override;
  private:
  };
  static constexpr uint32_t TRANSACTION_LogThis = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<ILoggableInterface> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ILoggableInterface>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ILoggableInterface>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ILoggableInterface>& impl);
  static const std::shared_ptr<ILoggableInterface>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus LogThis(bool in_boolValue, std::vector<bool>* in_boolArray, int8_t in_byteValue, std::vector<uint8_t>* in_byteArray, char16_t in_charValue, std::vector<char16_t>* in_charArray, int32_t in_intValue, std::vector<int32_t>* in_intArray, int64_t in_longValue, std::vector<int64_t>* in_longArray, float in_floatValue, std::vector<float>* in_floatArray, double in_doubleValue, std::vector<double>* in_doubleArray, const std::string& in_stringValue, std::vector<std::string>* in_stringArray, std::vector<std::string>* in_listValue, const ::aidl::android::aidl::loggable::Data& in_dataValue, const ::ndk::SpAIBinder& in_binderValue, ::ndk::ScopedFileDescriptor* in_pfdValue, std::vector<::ndk::ScopedFileDescriptor>* in_pfdArray, std::vector<std::string>* _aidl_return) = 0;
private:
  static std::shared_ptr<ILoggableInterface> default_impl;
};
class ILoggableInterfaceDefault : public ILoggableInterface {
public:
  ::ndk::ScopedAStatus LogThis(bool in_boolValue, std::vector<bool>* in_boolArray, int8_t in_byteValue, std::vector<uint8_t>* in_byteArray, char16_t in_charValue, std::vector<char16_t>* in_charArray, int32_t in_intValue, std::vector<int32_t>* in_intArray, int64_t in_longValue, std::vector<int64_t>* in_longArray, float in_floatValue, std::vector<float>* in_floatArray, double in_doubleValue, std::vector<double>* in_doubleArray, const std::string& in_stringValue, std::vector<std::string>* in_stringArray, std::vector<std::string>* in_listValue, const ::aidl::android::aidl::loggable::Data& in_dataValue, const ::ndk::SpAIBinder& in_binderValue, ::ndk::ScopedFileDescriptor* in_pfdValue, std::vector<::ndk::ScopedFileDescriptor>* in_pfdArray, std::vector<std::string>* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace loggable
}  // namespace aidl
}  // namespace android
}  // namespace aidl
