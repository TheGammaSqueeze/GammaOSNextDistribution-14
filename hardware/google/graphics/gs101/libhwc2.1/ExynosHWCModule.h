/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef ANDROID_EXYNOS_HWC_MODULE_H_
#define ANDROID_EXYNOS_HWC_MODULE_H_

#include <array>

#include "ExynosHWC.h"

#define G2D_MAX_SRC_NUM 3

#define VSYNC_DEV_PREFIX "/sys/devices/platform/"
#define PSR_DEV_NAME  "1c300000.decon_f/psr_info"
#define VSYNC_DEV_NAME_EXT  "19050000.decon_t/vsync"
#define DP_LINK_NAME	"130b0000.displayport"
#define DP_UEVENT_NAME	"change@/devices/platform/%s/extcon/extcon0"
#define DP_CABLE_STATE_NAME "/sys/devices/platform/%s/extcon/extcon0/cable.0/state"
#define BRIGHTNESS_SYSFS_NODE "/sys/class/backlight/panel%d-backlight/brightness"
#define MAX_BRIGHTNESS_SYSFS_NODE "/sys/class/backlight/panel%d-backlight/max_brightness"

#define IDMA(x) static_cast<decon_idma_type>(x)

#define MPP_G2D_CAPACITY    3.5

enum {
    HWC_DISPLAY_NONE_BIT = 0
};

#define MAX_NAME_SIZE   32
struct exynos_display_t {
    uint32_t type;
    uint32_t index;
    std::string display_name;
    std::string decon_node_name;
    std::string vsync_node_name;
};

#define PRIMARY_MAIN_BASE_WIN   2
#define EXTERNAL_MAIN_BASE_WIN  4

/******** Description about display bit ********/
/*   DISPLAY BIT = 1 << (DISPLAY_MODE_MASK_LEN * display mode
 *                       + SECOND_DISPLAY_START_BIT * display index
 *                       + display type);
 *   ex) HWC_DISPLAY_EXTERNAL2_BIT = 1 << (DISPLAY_MODE_MASK_LEN * display mode(0)
 *                                         + SECOND_DISPLAY_START_BIT * display index(1)
 *                                         + displayy type(1))
 *                                 = 1 << 5
 *   PRIMARY MAIN MODE :
 *      0 bit : HWC_DISPLAY_PRIMARY_BIT,
 *      1 bit : HWC_DISPLAY_EXTERNAL_BIT,
 *      2 bit : HWC_DISPLAY_VIRTUAL_BIT,
 *      5 bit : HWC_DISPLAY_EXTERNAL2_BIT,
 *   EXTERNAL MAIN MODE :
 *      8 bit : EXTERNAL_MAIN_DISPLAY_PRIMARY_BIT,
 *      9 bit : EXTERNAL_MAIN_DISPLAY_EXTERNAL_BIT,
 *      10 bit : EXTERNAL_MAIN_DISPLAY_VIRTUAL_BIT,
 ***************************************************/

#define DISPLAY_MODE_MASK_LEN    8
#define DISPLAY_MODE_MASK_BIT    0xff
#define SECOND_DISPLAY_START_BIT   4

enum {
    DISPLAY_MODE_PRIMARY_MAIN = 0,  /* This is default mode */
    DISPLAY_MODE_EXTERNAL_MAIN,
    DISPLAY_MODE_NUM
};

/*
 * This is base window index of primary display for each display mode.
 * External display base window is always 0
 */
const uint32_t PRIMARY_DISP_BASE_WIN[] = {PRIMARY_MAIN_BASE_WIN, EXTERNAL_MAIN_BASE_WIN};

#define EXTERNAL_MAIN_DISPLAY_START_BIT (DISPLAY_MODE_MASK_LEN * DISPLAY_MODE_EXTERNAL_MAIN)
enum {
    EXTERNAL_MAIN_DISPLAY_PRIMARY_BIT = 1 << (EXTERNAL_MAIN_DISPLAY_START_BIT + HWC_DISPLAY_PRIMARY),
    EXTERNAL_MAIN_DISPLAY_EXTERNAL_BIT = 1 << (EXTERNAL_MAIN_DISPLAY_START_BIT + HWC_DISPLAY_EXTERNAL),
    EXTERNAL_MAIN_DISPLAY_VIRTUAL_BIT = 1 << (EXTERNAL_MAIN_DISPLAY_START_BIT + HWC_DISPLAY_VIRTUAL),
};

enum {
    HWC_RESERVE_DISPLAY_MAIN_BIT = 1,
    HWC_RESERVE_DISPLAY_MINOR_BIT = 2
};

namespace gs101 {

static const char *early_wakeup_node_0_base =
    "/sys/devices/platform/1c300000.drmdecon/early_wakeup";

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
    {MPP_DPP_GF, MPP_LOGICAL_DPP_GF, "DPP_GF2", 2, 0, HWC_RESERVE_DISPLAY_MINOR_BIT, 0, 0},
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS0", 0, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS1", 1, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS2", 2, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0}};

static const std::array<exynos_display_t, 2> AVAILABLE_DISPLAY_UNITS = {{
    {HWC_DISPLAY_PRIMARY, 0, "PrimaryDisplay", "/dev/dri/card0", ""},
    {HWC_DISPLAY_PRIMARY, 1, "SecondaryDisplay", "/dev/dri/card0", ""}
}};

} // namespace gs101

const exynos_mpp_t AVAILABLE_M2M_MPP_UNITS[] = {
#ifndef DISABLE_M2M_MPPS
    {MPP_G2D, MPP_LOGICAL_G2D_YUV, "G2D0-YUV_PRI", 0, 0, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_G2D, MPP_LOGICAL_G2D_YUV, "G2D0-YUV_PRI", 0, 1, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_G2D, MPP_LOGICAL_G2D_YUV, "G2D0-YUV_EXT", 0, 2, HWC_RESERVE_DISPLAY_MINOR_BIT, 0, 0},
    {MPP_G2D, MPP_LOGICAL_G2D_RGB, "G2D0-RGB_PRI", 0, 3, HWC_RESERVE_DISPLAY_MAIN_BIT, 0, 0},
    {MPP_G2D, MPP_LOGICAL_G2D_RGB, "G2D0-RGB_EXT", 0, 4, HWC_RESERVE_DISPLAY_MINOR_BIT, 0, 0},
    {MPP_G2D, MPP_LOGICAL_G2D_COMBO, "G2D0-COMBO_VIR", 0, 5, HWC_RESERVE_DISPLAY_MINOR_BIT, 0, 0}
#endif
};

#endif
