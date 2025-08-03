/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef _EXYNOSMPPTYPE_H
#define _EXYNOSMPPTYPE_H

#include <system/graphics.h>
#include <utils/String8.h>

#include <unordered_map>

#include "DeconHeader.h"

using namespace android;

/*
 * physical types
 * Resources are sorted by physical type.
 * Resource that has smaller type value is assigned first.
 */
typedef enum {
    MPP_DPP_G = 0,
    MPP_DPP_GF,
    MPP_DPP_GFS,
    MPP_DPP_VG,
    MPP_DPP_VGS,
    MPP_DPP_VGF,
    MPP_DPP_VGFS,
    MPP_DPP_VGRFS,
    MPP_DPP_NUM,

    MPP_MSC,
    MPP_G2D,
    MPP_P_TYPE_MAX
} mpp_phycal_type_t;

/* logical types */
typedef enum {
    MPP_LOGICAL_DPP_G = 0x01,
    MPP_LOGICAL_DPP_GF = 0x02,
    MPP_LOGICAL_DPP_GFS = 0x03,
    MPP_LOGICAL_DPP_VG = 0x04,
    MPP_LOGICAL_DPP_VGS = 0x08,
    MPP_LOGICAL_DPP_VGF = 0x10,
    MPP_LOGICAL_DPP_VGFS = 0x20,
    MPP_LOGICAL_DPP_VGRFS = 0x40,
    MPP_LOGICAL_MSC = 0x100,
    MPP_LOGICAL_MSC_YUV = 0x200,
    MPP_LOGICAL_G2D_YUV = 0x1000,
    MPP_LOGICAL_G2D_RGB = 0x2000,
    MPP_LOGICAL_G2D_COMBO = 0x4000,
    /*
     * Increase MPP_LOGICAL_TYPE_NUM
     * if type is added
     */
    MPP_LOGICAL_TYPE_NUM = 13
} mpp_logical_type_t;

enum {
    MPP_ATTR_AFBC                   = 0x00000001,
    MPP_ATTR_WINDOW_UPDATE          = 0x00000002,
    MPP_ATTR_BLOCK_MODE             = 0x00000004,
    MPP_ATTR_USE_CAPA               = 0x00000008,
    MPP_ATTR_FLIP_H                 = 0x00000020,
    MPP_ATTR_FLIP_V                 = 0x00000040,
    MPP_ATTR_ROT_90                 = 0x00000080,
    MPP_ATTR_SCALE                  = 0x00000800,
    MPP_ATTR_DIM                    = 0x00001000,
    MPP_ATTR_LAYER_TRANSFORM        = 0x00002000,

    MPP_ATTR_WCG                    = 0x00100000,
    MPP_ATTR_HDR10                  = 0x00200000,

    MPP_ATTR_HDR10PLUS              = 0x10000000,
};

// Resource TDM (Time-Division Muliplexing)
typedef enum {
    TDM_ATTR_SRAM_AMOUNT,
    TDM_ATTR_AFBC,
    TDM_ATTR_SBWC,
    TDM_ATTR_ITP, // CSC //
    TDM_ATTR_ROT_90,
    TDM_ATTR_SCALE,
    TDM_ATTR_WCG,
    TDM_ATTR_MAX,
} tdm_attr_t;

typedef enum {
    LS_DPUF,
    LS_DPUF_AXI
} LoadSharing_t;

typedef struct {
    String8 name;
    LoadSharing_t loadSharing;
} TDMInfo_t;

extern std::unordered_map<tdm_attr_t, TDMInfo_t> HWAttrs;

typedef struct feature_support_t {
    mpp_phycal_type_t hwType; /* MPP_DPP_VG, MPP_DPP_VGFS, ... */
    uint64_t attr;
} feature_support_t;

typedef struct transform_map {
    android_transform_t hal_tr;
    uint32_t hwc_tr;
} transform_map_t;

const transform_map_t transform_map_table [] =
{
    {HAL_TRANSFORM_FLIP_H, MPP_ATTR_FLIP_H},
    {HAL_TRANSFORM_FLIP_V, MPP_ATTR_FLIP_V},
    {HAL_TRANSFORM_ROT_90, MPP_ATTR_ROT_90},
};

typedef struct dpu_attr_map {
    uint32_t dpp_attr;
    uint32_t hwc_attr = 0;
} dpu_attr_map_t;

typedef struct dpp_channel_map {
    mpp_phycal_type_t type;
    uint32_t index;
    uint32_t    idma; // DECON_IDMA
    decon_idma_type channel;
} dpp_channel_map_t;

/*
 * pre_assign_info: all display_descriptors that want to reserve
 */
struct exynos_mpp_t {
    int physicalType;
    int logicalType;
    char name[16];
    uint32_t physical_index;
    uint32_t logical_index;
    uint32_t pre_assign_info;
    // For TDM
    uint32_t hw_block_index;
    uint32_t axi_port_index;
};

#endif
