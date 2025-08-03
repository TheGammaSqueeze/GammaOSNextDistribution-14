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

#ifndef VENDOR_LINEAGE_LIVEDISPLAY_V2_0_DISPLAYMODES_H
#define VENDOR_LINEAGE_LIVEDISPLAY_V2_0_DISPLAYMODES_H

#include <vendor/lineage/livedisplay/2.0/IDisplayModes.h>

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace legacymm {

using ::android::hardware::Return;
using ::android::hardware::Void;

class DisplayModes : public IDisplayModes {
  public:
    DisplayModes(void* libHandle);

    bool isSupported();

    // Methods from ::vendor::lineage::livedisplay::V2_0::IDisplayModes follow.
    Return<void> getDisplayModes(getDisplayModes_cb _hidl_cb) override;
    Return<void> getCurrentDisplayMode(getCurrentDisplayMode_cb _hidl_cb) override;
    Return<void> getDefaultDisplayMode(getDefaultDisplayMode_cb _hidl_cb) override;
    Return<bool> setDisplayMode(int32_t modeID, bool makeDefault) override;

  private:
    void* mLibHandle;

    int (*disp_api_supported)(int32_t, int32_t);
    int (*disp_api_get_num_display_modes)(int32_t, int32_t, int*);
    int (*disp_api_get_display_modes)(int32_t, int32_t, void*, int);
    int (*disp_api_get_active_display_mode)(int32_t, int*, uint32_t*);
    int (*disp_api_set_active_display_mode)(int32_t, int);
    int (*disp_api_get_default_display_mode)(int32_t, int*);
    int (*disp_api_set_default_display_mode)(int32_t, int);

    std::vector<DisplayMode> getDisplayModesInternal();
    DisplayMode getDisplayModeById(int32_t id);
    DisplayMode getCurrentDisplayModeInternal();
    DisplayMode getDefaultDisplayModeInternal();
};

}  // namespace legacymm
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor

#endif  // VENDOR_LINEAGE_LIVEDISPLAY_V2_0_DISPLAYMODES_H
