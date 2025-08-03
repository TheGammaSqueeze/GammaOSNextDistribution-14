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

#include "PowerShare.h"
#include <fstream>

#define WLC_DEV_DIR "/sys/class/power_supply/wireless/device"
#define RTX_ENABLE_PATH WLC_DEV_DIR "/rtx"

/*
 * Write value to path and close file.
 */
template <typename T>
static void set(const std::string& path, const T& value) {
    std::ofstream file(path);
    file << value;
}

template <typename T>
static T get(const std::string& path, const T& def) {
    std::ifstream file(path);
    T result;

    file >> result;
    return file.fail() ? def : result;
}

namespace vendor::lineage::powershare::pixel {

Return<bool> PowerShare::isEnabled() {
    return get(RTX_ENABLE_PATH, 0) == 1;
}

Return<bool> PowerShare::setEnabled(bool enable) {
    set(RTX_ENABLE_PATH, enable ? 1 : 0);
    return isEnabled();
}

Return<uint32_t> PowerShare::getMinBattery() {
    return 0;
}

Return<uint32_t> PowerShare::setMinBattery(uint32_t) {
    return 0;
}

}  // namespace vendor::lineage::powershare::pixel
