/*
 * Copyright (C) 2021 The LineageOS Project
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

#include <android-base/properties.h>

#include "GloveMode.h"

#define TOUCH_SENSITIVITY_PROP "persist.vendor.touch_sensitivity_mode"

namespace vendor::lineage::touch::pixel {

// Methods from ::vendor::lineage::touch::V1_0::IGloveMode follow.
Return<bool> GloveMode::isEnabled() {
    return android::base::GetBoolProperty(TOUCH_SENSITIVITY_PROP, false);
}

Return<bool> GloveMode::setEnabled(bool enabled) {
    return android::base::SetProperty(TOUCH_SENSITIVITY_PROP, enabled ? "1" : "0");
}

}  // namespace vendor::lineage::touch::pixel
