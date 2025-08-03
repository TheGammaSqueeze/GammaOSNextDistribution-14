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

#if __ANDROID__

#include <ditto/binder.h>
#include <ditto/binder_request.h>
#include <ditto/logger.h>
#include <cutils/ashmem.h>
#include <sys/mman.h>


namespace dittosuite {

BinderRequest::BinderRequest(const std::string& kName, const Params& params,
                             const std::string& service_name)
    : Instruction(kName, params), service_name_(service_name) {}

BinderRequestDitto::BinderRequestDitto(const Params& params, const std::string& service_name)
    : BinderRequest(kName, params, service_name) {}

void BinderRequestDitto::RunSingle() {
  const int8_t c = 1;

  int8_t ret = service_->sync(c);
  if (ret != (~c)) {
    LOGF("Wrong result, expected: " + std::to_string(~c) + ", but got: " + std::to_string(ret));
  }
  LOGD("Returned from Binder request: " + std::to_string(ret));
}

void BinderRequestDitto::SetUp() {
  LOGD("Starting binder requester for service: " + service_name_);
  service_ = getBinderService<IDittoBinder>(service_name_);
  service_->start();
  Instruction::SetUp();
}

void BinderRequestDitto::TearDownSingle(bool is_last) {
  Instruction::TearDownSingle(is_last);
  if (is_last) {
    LOGD("This is the last, sending termination request");
    service_->end();
  }
}

BinderRequestMountService::BinderRequestMountService(const Params& params)
    : BinderRequest(kName, params, "mount") {}

void BinderRequestMountService::RunSingle() {
  bool ret = service_->isUsbMassStorageConnected();
  LOGD("Returned from Binder request: " + std::to_string(ret));
}

void BinderRequestMountService::SetUp() {
  LOGD("Starting binder requester for service: " + service_name_);
  service_ = getBinderService<android::IMountService>(service_name_);
  Instruction::SetUp();
}

void BinderRequestMountService::TearDownSingle(bool last) {
  Instruction::TearDownSingle(last);
}

GenericBinderRequest::GenericBinderRequest(const Params& params,
    std::string service_name, int32_t code,
    const google::protobuf::RepeatedPtrField
      <dittosuiteproto::BinderRequest_GenericService_ParcelInput> parcel_input)
    : BinderRequest(kName, params, service_name), parcel_input_(parcel_input),
     service_name_(service_name), code_(code) {}

void GenericBinderRequest::SetUp() {
  android::sp<android::IServiceManager> sm = android::defaultServiceManager();
  service_ = sm->checkService(String16(service_name_.c_str(), service_name_.length()));
}

void GenericBinderRequest::TearDownSingle(bool last) {
  Instruction::TearDownSingle(last);
}

int ParseAshmemWithPath(std::string path, android::Parcel& parcel) {
  int fd = open(path.c_str(), O_RDONLY);
  struct stat statbuf;
  int afd = -1;
  void* ptr = MAP_FAILED;
  if (fd < 0) {
    LOGF("Could not open " + path);
    return -1;
  }
  if (fstat(fd, &statbuf) != 0) {
    LOGF("Could not stat " + path);
    goto error_close_fd;
  }
  afd = ashmem_create_region("ditto", statbuf.st_size);
  if (afd < 0) {
    LOGF("ashmem_create_region failed " + path);
    goto error_close_fd;
  }
  ptr = mmap(NULL, statbuf.st_size, PROT_READ | PROT_WRITE, MAP_SHARED, afd, 0);
  if (ptr == MAP_FAILED) {
    LOGF("mmap failed " + path);
    goto error_close_afd;
  }
  if (read(fd, ptr, statbuf.st_size) < 0) {
    LOGF("read failed " + path);
    goto error_unmap;
  }
  if (parcel.writeFileDescriptor(afd, true /* take ownership */) == android::OK) {
    // successfully parsed. unmap and afd close is done by the binder server.
    close(fd);
    return 0;
  }
  LOGF("writeFileDescriptor failed " + path);

error_unmap:
  munmap(ptr, statbuf.st_size);
error_close_afd:
  close(afd);
error_close_fd:
  close(fd);
  return -1;
}

int ParseParcelString(const google::protobuf::RepeatedPtrField
      <dittosuiteproto::BinderRequest_GenericService_ParcelInput>& input,
      android::Parcel& parcel) {
  for (const auto &it : input ) {
    std::string data_str = it.data();
    switch (it.type()) {
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_I32: {
        parcel.writeInt32(atoi(data_str.c_str()));
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_I64: {
        parcel.writeInt64(atoll(data_str.c_str()));
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_STRING_16: {
        parcel.writeString16(String16(data_str.c_str(), data_str.length()));
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_F: {
        parcel.writeFloat(atof(data_str.c_str()));
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_D: {
        parcel.writeDouble(atof(data_str.c_str()));
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_NULL_: {
        parcel.writeStrongBinder(nullptr);
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_FD: {
        parcel.writeFileDescriptor(atoi(data_str.c_str()), true /* take ownership */);
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_FD_PATH: {
        int fd = open(data_str.c_str(), O_RDONLY);
        if (fd < 0) {
          LOGF("Could not open " + data_str);
          return -1;
        }
        parcel.writeFileDescriptor(fd, true /* take ownership */);
        break;
      }
      case dittosuiteproto::BinderRequest_GenericService_ParcelInput_Type_ASHMEM_FD_PATH: {
        if (ParseAshmemWithPath(data_str.c_str(), parcel) < 0) {
          return -1;
        }
        break;
      }
      default:
        break;
    }
  }
  return  0;
}

void GenericBinderRequest::RunSingle() {
  android::Parcel data, reply;
  data.markForBinder(service_);
  data.writeInterfaceToken(service_ ? service_->getInterfaceDescriptor() : String16());
  if (ParseParcelString(parcel_input_, data)) {
    LOGF("Error parsing parcel string\n");
    return;
  }

  service_->transact(code_, data, &reply);

  std::stringstream ss;
  ss << reply;
  LOGD("Returned from Binder transact:\n" + ss.str());
}
}  // namespace dittosuite

#endif
