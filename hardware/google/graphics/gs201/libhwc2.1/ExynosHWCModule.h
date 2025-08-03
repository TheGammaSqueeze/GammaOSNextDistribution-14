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

#ifndef ANDROID_EXYNOS_HWC_MODULE_GS201_H_
#define ANDROID_EXYNOS_HWC_MODULE_GS201_H_

#include "../../gs101/libhwc2.1/ExynosHWCModule.h"

namespace gs201 {

static const char *early_wakeup_node_0_base =
    "/sys/devices/platform/1c240000.drmdecon/early_wakeup";

static const dpp_channel_map_t idma_channel_map[] = {
    /* GF physical index is switched to change assign order */
    /* DECON_IDMA is not used */
    {MPP_DPP_GF,     0, IDMA(0),   IDMA(0)},
    {MPP_DPP_VGRFS,  0, IDMA(1),   IDMA(1)},
    {MPP_DPP_GF,     1, IDMA(2),   IDMA(2)},
    {MPP_DPP_VGRFS,  1, IDMA(3),   IDMA(3)},
    {MPP_DPP_GF,     2, IDMA(4),   IDMA(4)},
    {MPP_DPP_VGRFS,  2, IDMA(5),   IDMA(5)},
    {MPP_P_TYPE_MAX, 0, IDMA(6),   IDMA(6)}, // not idma but..
    {static_cast<mpp_phycal_type_t>(MAX_DECON_DMA_TYPE), 0, MAX_DECON_DMA_TYPE, IDMA(7)}
};

static const exynos_mpp_t available_otf_mpp_units[] = {
    {MPP_DPP_GF, MPP_LOGICAL_DPP_GF, "DPP_GF0", 0, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_GF, MPP_LOGICAL_DPP_GF, "DPP_GF1", 1, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_GF, MPP_LOGICAL_DPP_GF, "DPP_GF2", 2, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS0", 0, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS1", 1, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS2", 2, 0, HWC_RESERVE_DISPLAY_MINOR_BIT, 0, 0}
};

static const std::array<exynos_display_t, 2> AVAILABLE_DISPLAY_UNITS = {{
    {HWC_DISPLAY_PRIMARY, 0, "PrimaryDisplay", "/dev/dri/card0", ""},
    {HWC_DISPLAY_PRIMARY, 1, "SecondaryDisplay", "/dev/dri/card0", ""}
}};

} // namespace gs201

#endif // ANDROID_EXYNOS_HWC_MODULE_GS201_H_
