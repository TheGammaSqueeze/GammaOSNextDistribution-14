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

#include <ditto/logger.h>

namespace dittosuite {

// Client
BpDittoBinder::BpDittoBinder(const sp<IBinder>& impl) : BpInterface<IDittoBinder>(impl) {
  LOGD("BpDittoBinder::BpDittoBinder()");
}

void BpDittoBinder::async() {
  Parcel data, reply;
  data.writeInterfaceToken(IDittoBinder::getInterfaceDescriptor());
  data.writeString16(String16(""));
  remote()->transact(ASYNC, data, &reply, IBinder::FLAG_ONEWAY);  // asynchronous call
  LOGD("BpDittoBinder::async()");
}

int8_t BpDittoBinder::sync(int8_t c) {
  Parcel data, reply;
  data.writeInterfaceToken(IDittoBinder::getInterfaceDescriptor());
  data.writeByte(c);
  LOGD("BpDittoBinder::sync parcel to be sent:");
  remote()->transact(SYNC, data, &reply);
  LOGD("BpDittoBinder::sync transact reply");

  int8_t res;
  reply.readByte(&res);
  LOGD("BpDittoBinder::sync()");
  return res;
}

void BpDittoBinder::start() {
  Parcel data, reply;
  data.writeInterfaceToken(IDittoBinder::getInterfaceDescriptor());
  data.writeString16(String16(""));
  remote()->transact(START, data, &reply, IBinder::FLAG_ONEWAY);  // asynchronous call
  LOGD("BpDittoBinder::start()");
}

void BpDittoBinder::end() {
  Parcel data, reply;
  data.writeInterfaceToken(IDittoBinder::getInterfaceDescriptor());
  data.writeString16(String16(""));
  remote()->transact(END, data, &reply, IBinder::FLAG_ONEWAY);  // asynchronous call
  LOGD("BpDittoBinder::end()");
}

// IMPLEMENT_META_INTERFACE(DittoBinder, "DittoBinder");
//  Macro above expands to code below. Doing it by hand so we can log ctor and destructor calls.
const String16 IDittoBinder::descriptor("DittoBinder");
const String16& IDittoBinder::getInterfaceDescriptor() const {
  return IDittoBinder::descriptor;
}

sp<IDittoBinder> IDittoBinder::asInterface(const sp<IBinder>& obj) {
  sp<IDittoBinder> intr;
  if (obj != nullptr) {
    intr = static_cast<IDittoBinder*>(obj->queryLocalInterface(IDittoBinder::descriptor).get());
    if (intr == nullptr) {
      intr = new BpDittoBinder(obj);
    }
  }
  return intr;
}
IDittoBinder::IDittoBinder() {
  LOGD("IDittoBinder::IDittoBinder()");
}
IDittoBinder::~IDittoBinder() {
  LOGD("IDittoBinder::~IDittoBinder()");
}

// Server
status_t BnDittoBinder::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
  LOGD("BnDittoBinder::onTransact(), code: " + std::to_string(code));
  data.checkInterface(this);

  switch (code) {
    case START: {
      start();
      LOGD("BnDittoBinder::onTransact START, refcount: " + std::to_string(client_cnt_));
      return NO_ERROR;
    } break;
    case END: {
      end();
      LOGD("BnDittoBinder::onTransact END, refcount: " + std::to_string(client_cnt_));
      if (client_cnt_ <= 0) {
        LOGD("BnDittoBinder::onTransact END, unblocking thread pool");
        pthread_cond_signal(thread_condition_);
      }
      return NO_ERROR;
    } break;
    case ASYNC: {
      async();
      return NO_ERROR;
    } break;
    case SYNC: {
      LOGD("BnDittoBinder::onTransact SYNC");
      int8_t c = data.readByte();
      int8_t res = sync(c);
      if (!reply) {
        LOGF("No reply");
      }
      reply->writeByte(res);
      return NO_ERROR;
    } break;
    default:
      LOGD("BnDittoBinder::onTransact OTHER");
      return BBinder::onTransact(code, data, reply, flags);
  }
}

void DittoBinder::start() {
  LOGD("DittoBinder::start()");
  client_cnt_++;
}
void DittoBinder::end() {
  LOGD("DittoBinder::end()");
  client_cnt_--;
}

void DittoBinder::async() {
  LOGD("DittoBinder::async()");
}

int8_t DittoBinder::sync(int8_t c) {
  LOGD("DittoBinder::sync()");
  return ~c;
}

}  // namespace dittosuite

#endif
