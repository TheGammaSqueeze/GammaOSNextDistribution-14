// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "misc.h"

#include "aemu/base/memory/MemoryTracker.h"

#include <cstring>

static int s_apiLevel = -1;
static bool s_isPhone = false;

android::base::CpuUsage* s_cpu_usage = nullptr;
android::base::MemoryTracker* s_mem_usage = nullptr;

void emugl::setAvdInfo(bool phone, int apiLevel) {
    s_isPhone = phone;
    s_apiLevel = apiLevel;
}

void emugl::getAvdInfo(bool* phone, int* apiLevel) {
    if (phone) *phone = s_isPhone;
    if (apiLevel) *apiLevel = s_apiLevel;
}

void emugl::setCpuUsage(android::base::CpuUsage* usage) {
    s_cpu_usage = usage;
}

android::base::CpuUsage* emugl::getCpuUsage() {
    return s_cpu_usage;
}

void emugl::setMemoryTracker(android::base::MemoryTracker* usage) {
    s_mem_usage = usage;
}

android::base::MemoryTracker* emugl::getMemoryTracker() {
    return s_mem_usage;
}
