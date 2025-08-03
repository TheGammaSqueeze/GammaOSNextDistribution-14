/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "legacy_binder.h"

static binder_status_t LegacyBinder_OnTransact(AIBinder* _aidl_binder,
                                               transaction_code_t _aidl_code,
                                               const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_binder;
  (void)_aidl_code;

  int32_t value;
  if (binder_status_t status = AParcel_readInt32(_aidl_in, &value); status != STATUS_OK) {
    return status;
  }
  return AParcel_writeInt32(_aidl_out, value);
}

static void* LegacyBinder_OnCreate(void* args) {
  return args;
}

static void LegacyBinder_OnDestroy(void* userData) {
  (void)userData;
}

const AIBinder_Class* kLegacyBinderClass = []() {
  auto clazz = AIBinder_Class_define("LegacyBinder", LegacyBinder_OnCreate, LegacyBinder_OnDestroy,
                                     LegacyBinder_OnTransact);
  AIBinder_Class_disableInterfaceTokenHeader(clazz);
  return clazz;
}();
