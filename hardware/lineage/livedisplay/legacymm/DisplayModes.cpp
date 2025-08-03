/*
 * Copyright (C) 2019 The LineageOS Project
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

#include "DisplayModes.h"

#include <dlfcn.h>

#include "Constants.h"
#include "PictureAdjustment.h"
#include "Types.h"

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace legacymm {

DisplayModes::DisplayModes(void* libHandle) {
    mLibHandle = libHandle;
    disp_api_supported =
            reinterpret_cast<int (*)(int32_t, int32_t)>(dlsym(mLibHandle, "disp_api_supported"));
    disp_api_get_num_display_modes = reinterpret_cast<int (*)(int32_t, int32_t, int*)>(
            dlsym(mLibHandle, "disp_api_get_num_display_modes"));
    disp_api_get_display_modes = reinterpret_cast<int (*)(int32_t, int32_t, void*, int)>(
            dlsym(mLibHandle, "disp_api_get_display_modes"));
    disp_api_get_active_display_mode = reinterpret_cast<int (*)(int32_t, int*, uint32_t*)>(
            dlsym(mLibHandle, "disp_api_get_active_display_mode"));
    disp_api_set_active_display_mode = reinterpret_cast<int (*)(int32_t, int)>(
            dlsym(mLibHandle, "disp_api_set_active_display_mode"));
    disp_api_get_default_display_mode = reinterpret_cast<int (*)(int32_t, int*)>(
            dlsym(mLibHandle, "disp_api_get_default_display_mode"));
    disp_api_set_default_display_mode = reinterpret_cast<int (*)(int32_t, int)>(
            dlsym(mLibHandle, "disp_api_set_default_display_mode"));
}

bool DisplayModes::isSupported() {
    int count = 0;

    if (disp_api_supported == nullptr || disp_api_supported(0, DISPLAY_MODES_FEATURE) == 0) {
        return false;
    }

    if (disp_api_get_num_display_modes != nullptr) {
        if (disp_api_get_num_display_modes(0, 0, &count) == 0) {
            return count > 0;
        }
    }

    return false;
}

std::vector<DisplayMode> DisplayModes::getDisplayModesInternal() {
    std::vector<DisplayMode> modes;
    int count = 0;

    if (disp_api_get_num_display_modes == nullptr ||
        disp_api_get_num_display_modes(0, 0, &count) != 0) {
        return modes;
    }

    if (disp_api_get_display_modes != nullptr) {
        mm_disp_mode* tmp = new mm_disp_mode[count];
        for (int i = 0; i < count; i++) {
            tmp[i].id = -1;
            tmp[i].name = new char[128];
            tmp[i].len = 128;
        }

        if (disp_api_get_display_modes(0, 0, tmp, count) == 0) {
            for (int i = 0; i < count; i++) {
                modes.push_back(DisplayMode{tmp[i].id, std::string(tmp[i].name)});
            }
        }

        for (int i = 0; i < count; i++) {
            delete[] tmp[i].name;
        }

        delete[] tmp;
    }

    return modes;
}

DisplayMode DisplayModes::getDisplayModeById(int32_t id) {
    std::vector<DisplayMode> modes = getDisplayModesInternal();

    for (const DisplayMode& mode : modes) {
        if (mode.id == id) {
            return mode;
        }
    }

    return DisplayMode{-1, ""};
}

DisplayMode DisplayModes::getCurrentDisplayModeInternal() {
    int id = 0;
    uint32_t mask = 0;

    if (disp_api_get_active_display_mode != nullptr) {
        if (disp_api_get_active_display_mode(0, &id, &mask) == 0 && id >= 0) {
            return getDisplayModeById(id);
        }
    }

    return DisplayMode{-1, ""};
}

DisplayMode DisplayModes::getDefaultDisplayModeInternal() {
    int id = 0;

    if (disp_api_get_default_display_mode != nullptr) {
        if (disp_api_get_default_display_mode(0, &id) == 0 && id >= 0) {
            return getDisplayModeById(id);
        }
    }

    return DisplayMode{-1, ""};
}

// Methods from ::vendor::lineage::livedisplay::V2_0::IDisplayModes follow.
Return<void> DisplayModes::getDisplayModes(getDisplayModes_cb _hidl_cb) {
    _hidl_cb(getDisplayModesInternal());
    return Void();
}

Return<void> DisplayModes::getCurrentDisplayMode(getCurrentDisplayMode_cb _hidl_cb) {
    _hidl_cb(getCurrentDisplayModeInternal());
    return Void();
}

Return<void> DisplayModes::getDefaultDisplayMode(getDefaultDisplayMode_cb _hidl_cb) {
    _hidl_cb(getDefaultDisplayModeInternal());
    return Void();
}

Return<bool> DisplayModes::setDisplayMode(int32_t modeID, bool makeDefault) {
    DisplayMode currentMode = getCurrentDisplayModeInternal();

    if (currentMode.id >= 0 && currentMode.id == modeID) {
        return true;
    }

    DisplayMode mode = getDisplayModeById(modeID);
    if (mode.id < 0) {
        return false;
    }

    if (disp_api_set_active_display_mode == nullptr ||
        disp_api_set_active_display_mode(0, modeID)) {
        return false;
    }

    if (makeDefault && (disp_api_set_default_display_mode == nullptr ||
                        disp_api_set_default_display_mode(0, modeID))) {
        return false;
    }

    PictureAdjustment::updateDefaultPictureAdjustment();

    return true;
}

}  // namespace legacymm
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
