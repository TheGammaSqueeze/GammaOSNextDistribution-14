// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#if __ANDROID__

#include <ditto/binder.h>
#include <ditto/instruction.h>

#include <storage/IMountService.h>

#include <random>

#include <cstdint>

namespace dittosuite {

class BinderRequest : public Instruction {
 public:
  explicit BinderRequest(const std::string& kName, const Params& params,
                         const std::string& service_name);

 protected:
  std::string service_name_;

  virtual void RunSingle() = 0;
  virtual void SetUp() = 0;
  virtual void TearDownSingle(bool last) = 0;
};

class BinderRequestDitto : public BinderRequest {
 public:
  inline static const std::string kName = "binder_request_ditto";

  explicit BinderRequestDitto(const Params& params, const std::string& service_name);

 protected:
  void RunSingle() override;

 private:
  android::sp<IDittoBinder> service_;

  void SetUp() override;
  void TearDownSingle(bool is_last) override;
};

class BinderRequestMountService : public BinderRequest {
 public:
  inline static const std::string kName = "binder_request_ms";

  explicit BinderRequestMountService(const Params& params);

 protected:
  void RunSingle() override;

 private:
  android::sp<android::IMountService> service_;

  void SetUp() override;
  void TearDownSingle(bool last) override;
};

class GenericBinderRequest : public BinderRequest {
 public:
  inline static const std::string kName = "binder_request_generic";
  explicit GenericBinderRequest(const Params& params,
      std::string service_name,  int32_t code,
      const google::protobuf::RepeatedPtrField<dittosuiteproto::BinderRequest_GenericService_ParcelInput> parcel_input);

 protected:
  void RunSingle() override;
  const google::protobuf::RepeatedPtrField<dittosuiteproto::BinderRequest_GenericService_ParcelInput> parcel_input_;
  std::string service_name_;
  int32_t code_;
  android::sp<android::IBinder> service_;

 private:
  void SetUp() override;
  void TearDownSingle(bool is_last) override;
};
}  // namespace dittosuite

#endif
