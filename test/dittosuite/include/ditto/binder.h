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

#ifdef __ANDROID__

#include <binder/IInterface.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>

#include <ditto/logger.h>

using android::BnInterface;
using android::BpInterface;
using android::IBinder;
using android::IInterface;
using android::NO_ERROR;
using android::Parcel;
using android::sp;
using android::status_t;
using android::String16;

namespace dittosuite {

// AIDL interface
class IDittoBinder : public IInterface {
 public:
  DECLARE_META_INTERFACE(DittoBinder);

  enum { START = IBinder::FIRST_CALL_TRANSACTION, END, SYNC, ASYNC };

  // Sends an asynchronous request to the service
  virtual void async() = 0;

  // Sends a synchronous request to the service
  virtual int8_t sync(int8_t c) = 0;

  // This should be called when a new binder client is created, to refcount
  // number of callers.
  virtual void start() = 0;

  // This should be called when a binder client finishes. When the refcount
  // reaches 0, then the binder server can stop.
  virtual void end() = 0;
};

// Client
class BpDittoBinder : public BpInterface<IDittoBinder> {
 public:
  BpDittoBinder(const sp<IBinder>& impl);

  virtual void async();
  virtual int8_t sync(int8_t c);
  virtual void start();
  virtual void end();
};

// Server
class BnDittoBinder : public BnInterface<IDittoBinder> {
  virtual status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags = 0);

 protected:
  std::atomic_int client_cnt_;
  pthread_cond_t* thread_condition_;
};

class DittoBinder : public BnDittoBinder {
 public:
  DittoBinder() = delete;
  DittoBinder(pthread_cond_t* thread_condition) { thread_condition_ = thread_condition; }

 private:
  virtual void async();
  virtual int8_t sync(int8_t c);
  virtual void start();
  virtual void end();
};

template <class T>
android::sp<T> getBinderService(const std::string& service_name) {
  LOGD("Getting default Binder ServiceManager");
  android::sp<android::IServiceManager> sm = android::defaultServiceManager();
  if (!sm) {
    LOGF("No Binder ServiceManager found");
  }

  LOGD("Getting Binder Service: " + service_name);
  android::sp<android::IBinder> binder = sm->waitForService(android::String16(service_name.c_str()));
  if (!binder) {
    LOGF("Unable to fetch Binder Interface");
  }

  LOGD("Getting Binder Service interface");
  android::sp<T> interface = android::interface_cast<T>(binder);
  if (!interface) {
    LOGF("Unable to cast Binder Service");
  }

  return interface;
}


}  // namespace dittosuite

#endif
