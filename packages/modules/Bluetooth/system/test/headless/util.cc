/*
 * Copyright 2022 The Android Open Source Project
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

#include <string>

#include "include/hardware/bluetooth.h"
#include "osi/include/properties.h"

bool is_android_running() {
#ifdef __ANDROID__
  char value[PROPERTY_VALUE_MAX];
  osi_property_get("init.svc.zygote", value, "running");
  if (!strncmp("running", value, PROPERTY_VALUE_MAX)) {
    return true;
  }
#endif
  return false;
}
