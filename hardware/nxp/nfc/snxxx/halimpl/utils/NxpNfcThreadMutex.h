/*
 *
 *  Copyright 2021-2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#pragma once

#include <pthread.h>

class NfcHalThreadMutex {
 public:
  NfcHalThreadMutex();
  virtual ~NfcHalThreadMutex();
  void lock();
  void unlock();
  operator pthread_mutex_t*() { return &mMutex; }

 private:
  pthread_mutex_t mMutex;
};

class NfcHalThreadCondVar : public NfcHalThreadMutex {
 public:
  NfcHalThreadCondVar();
  virtual ~NfcHalThreadCondVar();
  void signal();
  void timedWait(struct timespec* time);
  operator pthread_cond_t*() { return &mCondVar; }
  operator pthread_mutex_t*() {
    return NfcHalThreadMutex::operator pthread_mutex_t*();
  }

 private:
  pthread_cond_t mCondVar;
};

class NfcHalAutoThreadMutex {
 public:
  NfcHalAutoThreadMutex(NfcHalThreadMutex& m);
  virtual ~NfcHalAutoThreadMutex();
  operator NfcHalThreadMutex&() { return mm; }

 private:
  NfcHalThreadMutex& mm;
};
