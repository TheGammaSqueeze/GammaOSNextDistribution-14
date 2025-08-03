/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#define LOG_TAG "aidl_ndk_service"

#include <aidl/android/aidl/fixedsizearray/FixedSizeArrayExample.h>
#include <aidl/android/aidl/loggable/BnLoggableInterface.h>
#include <aidl/android/aidl/loggable/Data.h>
#include <aidl/android/aidl/test/trunk/BnTrunkStableTest.h>
#include <aidl/android/aidl/test/trunk/ITrunkStableTest.h>
#include <aidl/android/aidl/tests/BackendType.h>
#include <aidl/android/aidl/tests/BnCircular.h>
#include <aidl/android/aidl/tests/BnNamedCallback.h>
#include <aidl/android/aidl/tests/BnNewName.h>
#include <aidl/android/aidl/tests/BnOldName.h>
#include <aidl/android/aidl/tests/BnTestService.h>
#include <aidl/android/aidl/tests/ICircular.h>
#include <aidl/android/aidl/tests/INamedCallback.h>
#include <aidl/android/aidl/tests/ITestService.h>
#include <aidl/android/aidl/tests/Union.h>
#include <aidl/android/aidl/tests/extension/MyExt.h>
#include <aidl/android/aidl/tests/extension/MyExt2.h>
#include <aidl/android/aidl/tests/nested/BnNestedService.h>
#include <aidl/android/aidl/versioned/tests/BnFooInterface.h>
#include <aidl/android/aidl/versioned/tests/IFooInterface.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <tests/simple_parcelable_ndk.h>

#include <map>
#include <mutex>
#include <optional>
#include <sstream>
#include <string>
#include <vector>

#include <unistd.h>

using aidl::android::aidl::tests::BackendType;
using aidl::android::aidl::tests::BnCircular;
using aidl::android::aidl::tests::BnNamedCallback;
using aidl::android::aidl::tests::BnNewName;
using aidl::android::aidl::tests::BnOldName;
using aidl::android::aidl::tests::BnTestService;
using aidl::android::aidl::tests::ByteEnum;
using aidl::android::aidl::tests::CircularParcelable;
using aidl::android::aidl::tests::ConstantExpressionEnum;
using aidl::android::aidl::tests::ICircular;
using aidl::android::aidl::tests::INamedCallback;
using aidl::android::aidl::tests::INewName;
using aidl::android::aidl::tests::IntEnum;
using aidl::android::aidl::tests::IOldName;
using aidl::android::aidl::tests::ITestService;
using aidl::android::aidl::tests::LongEnum;
using aidl::android::aidl::tests::RecursiveList;
using aidl::android::aidl::tests::SimpleParcelable;
using aidl::android::aidl::tests::StructuredParcelable;
using aidl::android::aidl::tests::Union;
using aidl::android::aidl::tests::extension::ExtendableParcelable;

using std::optional;
using std::string;
using std::vector;

using ndk::ScopedAStatus;
using ndk::ScopedFileDescriptor;
using ndk::SharedRefBase;
using ndk::SpAIBinder;

namespace {

class NamedCallback : public BnNamedCallback {
 public:
  explicit NamedCallback(std::string name) : name_(name) {}

  ScopedAStatus GetName(std::string* ret) override {
    *ret = name_;
    return ScopedAStatus::ok();
  }

 private:
  std::string name_;
};

class OldName : public BnOldName {
 public:
  OldName() = default;
  ~OldName() = default;

  ScopedAStatus RealName(std::string* output) override {
    *output = std::string("OldName");
    return ScopedAStatus::ok();
  }
};

class NewName : public BnNewName {
 public:
  NewName() = default;
  ~NewName() = default;

  ScopedAStatus RealName(std::string* output) override {
    *output = std::string("NewName");
    return ScopedAStatus::ok();
  }
};

class Circular : public BnCircular {
 public:
  Circular(std::shared_ptr<ITestService> srv) : mSrv(srv) {}
  ~Circular() = default;

  ScopedAStatus GetTestService(std::shared_ptr<ITestService>* _aidl_return) override {
    *_aidl_return = mSrv;
    return ScopedAStatus::ok();
  }

 private:
  std::shared_ptr<ITestService> mSrv;
};

template <typename T>
ScopedAStatus ReverseArray(const vector<T>& input, vector<T>* repeated, vector<T>* _aidl_return) {
  ALOGI("Reversing array of length %zu", input.size());
  *repeated = input;
  *_aidl_return = input;
  std::reverse(_aidl_return->begin(), _aidl_return->end());
  return ScopedAStatus::ok();
}

template <typename T>
ScopedAStatus RepeatNullable(const optional<T>& input, optional<T>* _aidl_return) {
  ALOGI("Repeating nullable value");
  *_aidl_return = input;
  return ScopedAStatus::ok();
}

class NativeService : public BnTestService {
 public:
  NativeService() {}
  virtual ~NativeService() = default;

  void LogRepeatedStringToken(const std::string& token) {
    ALOGI("Repeating '%s' of length=%zu", token.c_str(), token.size());
  }

  template <typename T>
  void LogRepeatedToken(const T& token) {
    std::ostringstream token_str;
    token_str << token;
    ALOGI("Repeating token %s", token_str.str().c_str());
  }

  template <>
  void LogRepeatedToken(const char16_t& token) {
    ALOGI("Repeating token (char16_t) %d", token);
  }

  ScopedAStatus TestOneway() override { return ScopedAStatus::fromStatus(android::UNKNOWN_ERROR); }

  ScopedAStatus Deprecated() override { return ScopedAStatus::ok(); }

  ScopedAStatus RepeatBoolean(bool token, bool* _aidl_return) override {
    LogRepeatedToken(token ? 1 : 0);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatByte(int8_t token, int8_t* _aidl_return) override {
    LogRepeatedToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatChar(char16_t token, char16_t* _aidl_return) override {
    LogRepeatedToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatInt(int32_t token, int32_t* _aidl_return) override {
    LogRepeatedToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatLong(int64_t token, int64_t* _aidl_return) override {
    LogRepeatedToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatFloat(float token, float* _aidl_return) override {
    LogRepeatedToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatDouble(double token, double* _aidl_return) override {
    LogRepeatedToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatString(const std::string& token, std::string* _aidl_return) override {
    LogRepeatedStringToken(token);
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatByteEnum(ByteEnum token, ByteEnum* _aidl_return) override {
    ALOGI("Repeating ByteEnum token %s", toString(token).c_str());
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatIntEnum(IntEnum token, IntEnum* _aidl_return) override {
    ALOGI("Repeating IntEnum token %s", toString(token).c_str());
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatLongEnum(LongEnum token, LongEnum* _aidl_return) override {
    ALOGI("Repeating LongEnum token %s", toString(token).c_str());
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseBoolean(const vector<bool>& input, vector<bool>* repeated,
                               vector<bool>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseByte(const vector<uint8_t>& input, vector<uint8_t>* repeated,
                            vector<uint8_t>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseChar(const vector<char16_t>& input, vector<char16_t>* repeated,
                            vector<char16_t>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseInt(const vector<int32_t>& input, vector<int32_t>* repeated,
                           vector<int32_t>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseLong(const vector<int64_t>& input, vector<int64_t>* repeated,
                            vector<int64_t>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseFloat(const vector<float>& input, vector<float>* repeated,
                             vector<float>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseDouble(const vector<double>& input, vector<double>* repeated,
                              vector<double>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseString(const vector<std::string>& input, vector<std::string>* repeated,
                              vector<std::string>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseByteEnum(const vector<ByteEnum>& input, vector<ByteEnum>* repeated,
                                vector<ByteEnum>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseIntEnum(const vector<IntEnum>& input, vector<IntEnum>* repeated,
                               vector<IntEnum>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }
  ScopedAStatus ReverseLongEnum(const vector<LongEnum>& input, vector<LongEnum>* repeated,
                                vector<LongEnum>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }

  ScopedAStatus SetOtherTestService(const std::string& name,
                                    const std::shared_ptr<INamedCallback>& service,
                                    bool* _aidl_return) override {
    std::lock_guard<std::mutex> guard(service_map_mutex_);
    const auto& existing = service_map_.find(name);
    if (existing != service_map_.end() && existing->second == service) {
      *_aidl_return = true;

      return ScopedAStatus::ok();
    } else {
      *_aidl_return = false;
      service_map_[name] = service;

      return ScopedAStatus::ok();
    }
  }

  ScopedAStatus GetOtherTestService(const std::string& name,
                                    std::shared_ptr<INamedCallback>* returned_service) override {
    std::lock_guard<std::mutex> guard(service_map_mutex_);
    if (service_map_.find(name) == service_map_.end()) {
      service_map_[name] = SharedRefBase::make<NamedCallback>(name);
    }

    *returned_service = service_map_[name];
    return ScopedAStatus::ok();
  }

  ScopedAStatus VerifyName(const std::shared_ptr<INamedCallback>& service, const std::string& name,
                           bool* returned_value) override {
    std::string foundName;
    ScopedAStatus status = service->GetName(&foundName);

    if (status.isOk()) {
      *returned_value = foundName == name;
    }

    return status;
  }

  ScopedAStatus GetInterfaceArray(const vector<std::string>& names,
                                  vector<std::shared_ptr<INamedCallback>>* _aidl_return) override {
    vector<std::shared_ptr<INamedCallback>> services(names.size());
    for (size_t i = 0; i < names.size(); i++) {
      if (auto st = GetOtherTestService(names[i], &services[i]); !st.isOk()) {
        return st;
      }
    }
    *_aidl_return = std::move(services);
    return ScopedAStatus::ok();
  }

  ScopedAStatus VerifyNamesWithInterfaceArray(
      const vector<std::shared_ptr<INamedCallback>>& services, const vector<std::string>& names,
      bool* _aidl_ret) override {
    if (services.size() == names.size()) {
      for (size_t i = 0; i < services.size(); i++) {
        if (auto st = VerifyName(services[i], names[i], _aidl_ret); !st.isOk() || !*_aidl_ret) {
          return st;
        }
      }
      *_aidl_ret = true;
    } else {
      *_aidl_ret = false;
    }
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetNullableInterfaceArray(
      const optional<vector<optional<std::string>>>& names,
      optional<vector<std::shared_ptr<INamedCallback>>>* _aidl_ret) override {
    vector<std::shared_ptr<INamedCallback>> services;
    if (names.has_value()) {
      for (const auto& name : *names) {
        if (name.has_value()) {
          std::shared_ptr<INamedCallback> ret;
          if (auto st = GetOtherTestService(*name, &ret); !st.isOk()) {
            return st;
          }
          services.push_back(std::move(ret));
        } else {
          services.emplace_back();
        }
      }
    }
    *_aidl_ret = std::move(services);
    return ScopedAStatus::ok();
  }

  ScopedAStatus VerifyNamesWithNullableInterfaceArray(
      const optional<vector<std::shared_ptr<INamedCallback>>>& services,
      const optional<vector<optional<std::string>>>& names, bool* _aidl_ret) override {
    if (services.has_value() && names.has_value()) {
      if (services->size() == names->size()) {
        for (size_t i = 0; i < services->size(); i++) {
          if (services->at(i).get() && names->at(i).has_value()) {
            if (auto st = VerifyName(services->at(i), names->at(i).value(), _aidl_ret);
                !st.isOk() || !*_aidl_ret) {
              return st;
            }
          } else if (services->at(i).get() || names->at(i).has_value()) {
            *_aidl_ret = false;
            return ScopedAStatus::ok();
          } else {
            // ok if service=null && name=null
          }
        }
        *_aidl_ret = true;
      } else {
        *_aidl_ret = false;
      }
    } else {
      *_aidl_ret = services.has_value() == names.has_value();
    }
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetInterfaceList(
      const optional<vector<optional<std::string>>>& names,
      optional<vector<std::shared_ptr<INamedCallback>>>* _aidl_ret) override {
    return GetNullableInterfaceArray(names, _aidl_ret);
  }

  ScopedAStatus VerifyNamesWithInterfaceList(
      const optional<vector<std::shared_ptr<INamedCallback>>>& services,
      const optional<vector<optional<std::string>>>& names, bool* _aidl_ret) override {
    return VerifyNamesWithNullableInterfaceArray(services, names, _aidl_ret);
  }

  ScopedAStatus ReverseStringList(const vector<std::string>& input, vector<std::string>* repeated,
                                  vector<std::string>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }

  ScopedAStatus RepeatParcelFileDescriptor(const ScopedFileDescriptor& read,
                                           ScopedFileDescriptor* _aidl_return) override {
    ALOGE("Repeating parcel file descriptor");
    *_aidl_return = read.dup();
    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseParcelFileDescriptorArray(
      const vector<ScopedFileDescriptor>& input, vector<ScopedFileDescriptor>* repeated,
      vector<ScopedFileDescriptor>* _aidl_return) override {
    ALOGI("Reversing parcel descriptor array of length %zu", input.size());
    for (const auto& item : input) {
      repeated->push_back(item.dup());
    }

    for (auto i = input.rbegin(); i != input.rend(); i++) {
      _aidl_return->push_back(i->dup());
    }
    return ScopedAStatus::ok();
  }

  ScopedAStatus ThrowServiceException(int code) override {
    return ScopedAStatus::fromServiceSpecificError(code);
  }

  ScopedAStatus RepeatNullableIntArray(const optional<vector<int32_t>>& input,
                                       optional<vector<int32_t>>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableByteEnumArray(const optional<vector<ByteEnum>>& input,
                                            optional<vector<ByteEnum>>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableIntEnumArray(const optional<vector<IntEnum>>& input,
                                           optional<vector<IntEnum>>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableLongEnumArray(const optional<vector<LongEnum>>& input,
                                            optional<vector<LongEnum>>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableStringList(const optional<vector<optional<std::string>>>& input,
                                         optional<vector<optional<std::string>>>* _aidl_return) {
    ALOGI("Repeating nullable string list");
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableString(const optional<std::string>& input,
                                     optional<std::string>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableParcelable(const optional<ITestService::Empty>& input,
                                         optional<ITestService::Empty>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableParcelableList(
      const optional<vector<optional<ITestService::Empty>>>& input,
      optional<vector<optional<ITestService::Empty>>>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus RepeatNullableParcelableArray(
      const optional<vector<optional<ITestService::Empty>>>& input,
      optional<vector<optional<ITestService::Empty>>>* _aidl_return) {
    return RepeatNullable(input, _aidl_return);
  }

  ScopedAStatus TakesAnIBinder(const SpAIBinder& input) override {
    (void)input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus TakesANullableIBinder(const SpAIBinder& input) {
    (void)input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus TakesAnIBinderList(const vector<SpAIBinder>& input) override {
    (void)input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus TakesANullableIBinderList(const optional<vector<SpAIBinder>>& input) {
    (void)input;
    return ScopedAStatus::ok();
  }

  ScopedAStatus RepeatUtf8CppString(const string& token, string* _aidl_return) override {
    ALOGI("Repeating utf8 string '%s' of length=%zu", token.c_str(), token.size());
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }

  ScopedAStatus RepeatNullableUtf8CppString(const optional<string>& token,
                                            optional<string>* _aidl_return) override {
    if (!token) {
      ALOGI("Received null @utf8InCpp string");
      return ScopedAStatus::ok();
    }
    ALOGI("Repeating utf8 string '%s' of length=%zu", token->c_str(), token->size());
    *_aidl_return = token;
    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseUtf8CppString(const vector<string>& input, vector<string>* repeated,
                                     vector<string>* _aidl_return) {
    return ReverseArray(input, repeated, _aidl_return);
  }

  ScopedAStatus ReverseNullableUtf8CppString(const optional<vector<optional<string>>>& input,
                                             optional<vector<optional<string>>>* repeated,
                                             optional<vector<optional<string>>>* _aidl_return) {
    return ReverseUtf8CppStringList(input, repeated, _aidl_return);
  }

  ScopedAStatus ReverseUtf8CppStringList(const optional<vector<optional<string>>>& input,
                                         optional<vector<optional<string>>>* repeated,
                                         optional<vector<optional<string>>>* _aidl_return) {
    if (!input) {
      ALOGI("Received null list of utf8 strings");
      return ScopedAStatus::ok();
    }
    *_aidl_return = input;
    *repeated = input;
    std::reverse((*_aidl_return)->begin(), (*_aidl_return)->end());
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetCallback(bool return_null, std::shared_ptr<INamedCallback>* ret) {
    if (!return_null) {
      return GetOtherTestService(std::string("ABT: always be testing"), ret);
    }
    return ScopedAStatus::ok();
  }

  virtual ScopedAStatus FillOutStructuredParcelable(StructuredParcelable* parcelable) {
    parcelable->shouldBeJerry = "Jerry";
    parcelable->shouldContainThreeFs = {parcelable->f, parcelable->f, parcelable->f};
    parcelable->shouldBeByteBar = ByteEnum::BAR;
    parcelable->shouldBeIntBar = IntEnum::BAR;
    parcelable->shouldBeLongBar = LongEnum::BAR;
    parcelable->shouldContainTwoByteFoos = {ByteEnum::FOO, ByteEnum::FOO};
    parcelable->shouldContainTwoIntFoos = {IntEnum::FOO, IntEnum::FOO};
    parcelable->shouldContainTwoLongFoos = {LongEnum::FOO, LongEnum::FOO};

    parcelable->const_exprs_1 = ConstantExpressionEnum::decInt32_1;
    parcelable->const_exprs_2 = ConstantExpressionEnum::decInt32_2;
    parcelable->const_exprs_3 = ConstantExpressionEnum::decInt64_1;
    parcelable->const_exprs_4 = ConstantExpressionEnum::decInt64_2;
    parcelable->const_exprs_5 = ConstantExpressionEnum::decInt64_3;
    parcelable->const_exprs_6 = ConstantExpressionEnum::decInt64_4;
    parcelable->const_exprs_7 = ConstantExpressionEnum::hexInt32_1;
    parcelable->const_exprs_8 = ConstantExpressionEnum::hexInt32_2;
    parcelable->const_exprs_9 = ConstantExpressionEnum::hexInt32_3;
    parcelable->const_exprs_10 = ConstantExpressionEnum::hexInt64_1;

    parcelable->shouldSetBit0AndBit2 = StructuredParcelable::BIT0 | StructuredParcelable::BIT2;

    parcelable->u = Union::make<Union::ns>({1, 2, 3});
    parcelable->shouldBeConstS1 = Union::S1;
    return ScopedAStatus::ok();
  }

  ScopedAStatus RepeatExtendableParcelable(
      const ::aidl::android::aidl::tests::extension::ExtendableParcelable& ep,
      ::aidl::android::aidl::tests::extension::ExtendableParcelable* ep2) {
    ep2->a = ep.a;
    ep2->b = ep.b;
    std::optional<aidl::android::aidl::tests::extension::MyExt> myExt;
    ep.ext.getParcelable(&myExt);

    if (myExt == std::nullopt) {
      return ScopedAStatus::fromStatus(android::UNKNOWN_ERROR);
    }

    ep2->ext.setParcelable(*myExt);

    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseList(const RecursiveList& list, RecursiveList* ret) override {
    std::unique_ptr<RecursiveList> reversed;
    const RecursiveList* cur = &list;
    while (cur) {
      auto node = std::make_unique<RecursiveList>();
      node->value = cur->value;
      node->next = std::move(reversed);
      reversed = std::move(node);
      cur = cur->next.get();
    }
    *ret = std::move(*reversed);
    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseIBinderArray(const vector<SpAIBinder>& input, vector<SpAIBinder>* repeated,
                                    vector<SpAIBinder>* _aidl_return) override {
    *repeated = input;
    *_aidl_return = input;
    std::reverse(_aidl_return->begin(), _aidl_return->end());
    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseNullableIBinderArray(
      const std::optional<vector<SpAIBinder>>& input, std::optional<vector<SpAIBinder>>* repeated,
      std::optional<vector<SpAIBinder>>* _aidl_return) override {
    *repeated = input;
    *_aidl_return = input;
    if (*_aidl_return) {
      std::reverse((*_aidl_return)->begin(), (*_aidl_return)->end());
    }
    return ScopedAStatus::ok();
  }

  ScopedAStatus RepeatSimpleParcelable(const SimpleParcelable& input, SimpleParcelable* repeat,
                                       SimpleParcelable* _aidl_return) override {
    ALOGI("Repeated a SimpleParcelable %s", input.toString().c_str());
    *repeat = input;
    *_aidl_return = input;
    return ScopedAStatus::ok();
  }

  ScopedAStatus ReverseSimpleParcelables(const vector<SimpleParcelable>& input,
                                         vector<SimpleParcelable>* repeated,
                                         vector<SimpleParcelable>* _aidl_return) override {
    return ReverseArray(input, repeated, _aidl_return);
  }

  ScopedAStatus UnimplementedMethod(int32_t /* arg */, int32_t* /* _aidl_return */) override {
    return ScopedAStatus::fromStatus(STATUS_UNKNOWN_TRANSACTION);
  }

  ScopedAStatus GetOldNameInterface(std::shared_ptr<IOldName>* ret) {
    *ret = SharedRefBase::make<OldName>();
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetNewNameInterface(std::shared_ptr<INewName>* ret) {
    *ret = SharedRefBase::make<NewName>();
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetUnionTags(const std::vector<Union>& input,
                             std::vector<Union::Tag>* _aidl_return) override {
    std::vector<Union::Tag> tags;
    std::transform(input.begin(), input.end(), std::back_inserter(tags),
                   std::mem_fn(&Union::getTag));
    *_aidl_return = std::move(tags);
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetCppJavaTests(SpAIBinder* ret) {
    *ret = nullptr;
    return ScopedAStatus::ok();
  }

  ScopedAStatus getBackendType(BackendType* _aidl_return) override {
    *_aidl_return = BackendType::NDK;
    return ScopedAStatus::ok();
  }

  ScopedAStatus GetCircular(CircularParcelable* cp,
                            std::shared_ptr<ICircular>* _aidl_return) override {
    auto thiz = ref<ITestService>();
    cp->testService = thiz;
    *_aidl_return = SharedRefBase::make<Circular>(thiz);
    return ScopedAStatus::ok();
  }

 private:
  std::map<std::string, std::shared_ptr<INamedCallback>> service_map_;
  std::mutex service_map_mutex_;
};

class VersionedService : public aidl::android::aidl::versioned::tests::BnFooInterface {
 public:
  VersionedService() {}
  virtual ~VersionedService() = default;

  ScopedAStatus originalApi() override { return ScopedAStatus::ok(); }
  ScopedAStatus acceptUnionAndReturnString(
      const ::aidl::android::aidl::versioned::tests::BazUnion& u,
      std::string* _aidl_return) override {
    switch (u.getTag()) {
      case ::aidl::android::aidl::versioned::tests::BazUnion::intNum:
        *_aidl_return =
            std::to_string(u.get<::aidl::android::aidl::versioned::tests::BazUnion::intNum>());
        break;
    }
    return ScopedAStatus::ok();
  }
  ScopedAStatus returnsLengthOfFooArray(
      const vector<::aidl::android::aidl::versioned::tests::Foo>& foos, int32_t* ret) override {
    *ret = static_cast<int32_t>(foos.size());
    return ScopedAStatus::ok();
  }
  ScopedAStatus ignoreParcelablesAndRepeatInt(
      const ::aidl::android::aidl::versioned::tests::Foo& inFoo,
      ::aidl::android::aidl::versioned::tests::Foo* inoutFoo,
      ::aidl::android::aidl::versioned::tests::Foo* outFoo, int32_t value, int32_t* ret) override {
    (void)inFoo;
    (void)inoutFoo;
    (void)outFoo;
    *ret = value;
    return ScopedAStatus::ok();
  }
};

class LoggableInterfaceService : public aidl::android::aidl::loggable::BnLoggableInterface {
 public:
  LoggableInterfaceService() {}
  virtual ~LoggableInterfaceService() = default;

  virtual ScopedAStatus LogThis(bool, vector<bool>*, int8_t, vector<uint8_t>*, char16_t,
                                vector<char16_t>*, int32_t, vector<int32_t>*, int64_t,
                                vector<int64_t>*, float, vector<float>*, double, vector<double>*,
                                const std::string&, vector<std::string>*, vector<std::string>*,
                                const aidl::android::aidl::loggable::Data&, const SpAIBinder&,
                                ScopedFileDescriptor*, vector<ScopedFileDescriptor>*,
                                vector<std::string>* _aidl_return) override {
    *_aidl_return = vector<std::string>{std::string("loggable")};
    return ScopedAStatus::ok();
  }
};

using namespace aidl::android::aidl::tests::nested;
class NestedService : public BnNestedService {
 public:
  NestedService() {}
  virtual ~NestedService() = default;

  virtual ScopedAStatus flipStatus(const ParcelableWithNested& p,
                                   INestedService::Result* _aidl_return) {
    if (p.status == ParcelableWithNested::Status::OK) {
      _aidl_return->status = ParcelableWithNested::Status::NOT_OK;
    } else {
      _aidl_return->status = ParcelableWithNested::Status::OK;
    }
    return ScopedAStatus::ok();
  }
  virtual ScopedAStatus flipStatusWithCallback(
      ParcelableWithNested::Status status, const std::shared_ptr<INestedService::ICallback>& cb) {
    if (status == ParcelableWithNested::Status::OK) {
      return cb->done(ParcelableWithNested::Status::NOT_OK);
    } else {
      return cb->done(ParcelableWithNested::Status::OK);
    }
  }
};

using aidl::android::aidl::fixedsizearray::FixedSizeArrayExample;
class FixedSizeArrayService : public FixedSizeArrayExample::BnRepeatFixedSizeArray {
 public:
  FixedSizeArrayService() {}
  virtual ~FixedSizeArrayService() = default;

  ScopedAStatus RepeatBytes(const std::array<uint8_t, 3>& in_input,
                            std::array<uint8_t, 3>* out_repeated,
                            std::array<uint8_t, 3>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatInts(const std::array<int32_t, 3>& in_input,
                           std::array<int32_t, 3>* out_repeated,
                           std::array<int32_t, 3>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatBinders(const std::array<SpAIBinder, 3>& in_input,
                              std::array<SpAIBinder, 3>* out_repeated,
                              std::array<SpAIBinder, 3>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus RepeatParcelables(
      const std::array<FixedSizeArrayExample::IntParcelable, 3>& in_input,
      std::array<FixedSizeArrayExample::IntParcelable, 3>* out_repeated,
      std::array<FixedSizeArrayExample::IntParcelable, 3>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus Repeat2dBytes(const std::array<std::array<uint8_t, 3>, 2>& in_input,
                              std::array<std::array<uint8_t, 3>, 2>* out_repeated,
                              std::array<std::array<uint8_t, 3>, 2>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus Repeat2dInts(const std::array<std::array<int32_t, 3>, 2>& in_input,
                             std::array<std::array<int32_t, 3>, 2>* out_repeated,
                             std::array<std::array<int32_t, 3>, 2>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus Repeat2dBinders(const std::array<std::array<SpAIBinder, 3>, 2>& in_input,
                                std::array<std::array<SpAIBinder, 3>, 2>* out_repeated,
                                std::array<std::array<SpAIBinder, 3>, 2>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus Repeat2dParcelables(
      const std::array<std::array<FixedSizeArrayExample::IntParcelable, 3>, 2>& in_input,
      std::array<std::array<FixedSizeArrayExample::IntParcelable, 3>, 2>* out_repeated,
      std::array<std::array<FixedSizeArrayExample::IntParcelable, 3>, 2>* _aidl_return) override {
    *out_repeated = in_input;
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
};

class TrunkStableService : public aidl::android::aidl::test::trunk::BnTrunkStableTest {
 public:
  TrunkStableService() {}
  virtual ~TrunkStableService() = default;

  ScopedAStatus repeatParcelable(
      const aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input,
      aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override {
    *_aidl_return = input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatEnum(
      aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum input,
      aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override {
    *_aidl_return = input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatUnion(
      const aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input,
      aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override {
    *_aidl_return = input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus callMyCallback(
      const std::shared_ptr<aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& cb)
      override {
    if (!cb) return ScopedAStatus::fromStatus(android::UNEXPECTED_NULL);
    MyParcelable a, b;
    MyEnum c = MyEnum::ZERO, d = MyEnum::ZERO;
    MyUnion e, f;
    auto status = cb->repeatParcelable(a, &b);
    if (!status.isOk()) {
      return status;
    }
    status = cb->repeatEnum(c, &d);
    if (!status.isOk()) {
      return status;
    }
    status = cb->repeatUnion(e, &f);
    if (!status.isOk()) {
      return status;
    }
    MyOtherParcelable g, h;
    status = cb->repeatOtherParcelable(g, &h);
    return ScopedAStatus::ok();
  }

  ScopedAStatus repeatOtherParcelable(
      const aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input,
      aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return)
      override {
    *_aidl_return = input;
    return ScopedAStatus::ok();
  }
};

}  // namespace

int main(int /* argc */, char* /* argv */[]) {
  std::vector<SpAIBinder> binders = {
      SharedRefBase::make<NativeService>()->asBinder(),
      SharedRefBase::make<VersionedService>()->asBinder(),
      SharedRefBase::make<LoggableInterfaceService>()->asBinder(),
      SharedRefBase::make<NestedService>()->asBinder(),
      SharedRefBase::make<FixedSizeArrayService>()->asBinder(),
      SharedRefBase::make<TrunkStableService>()->asBinder(),
  };

  for (const SpAIBinder& binder : binders) {
    const char* desc = AIBinder_Class_getDescriptor(AIBinder_getClass(binder.get()));
    if (STATUS_OK != AServiceManager_addService(binder.get(), desc)) {
      ALOGE("Failed to add service %s", desc);
      return EXIT_FAILURE;
    }
  }

  ABinderProcess_joinThreadPool();
  return EXIT_FAILURE;  // should not reach
}
