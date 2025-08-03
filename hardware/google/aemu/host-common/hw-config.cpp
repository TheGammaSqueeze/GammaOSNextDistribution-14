// Copyright 2020 The Android Open Source Project
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
#include "hw-config.h"
#include "aemu/base/export.h"
#include <string.h>
#include <limits.h>
#include <stdlib.h>

/* the global variable containing the hardware config for this device */
AndroidHwConfig   android_hw[1];

AEMU_EXPORT AndroidHwConfig* aemu_get_android_hw() {
    return (AndroidHwConfig*)(android_hw);
}
