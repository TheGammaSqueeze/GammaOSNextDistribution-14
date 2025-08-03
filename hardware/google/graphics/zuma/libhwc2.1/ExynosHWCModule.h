/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ANDROID_EXYNOS_HWC_MODULE_ZUMA_H_
#define ANDROID_EXYNOS_HWC_MODULE_ZUMA_H_

#include "../../gs201/libhwc2.1/ExynosHWCModule.h"
#include "ExynosHWCHelper.h"

namespace zuma {

static const char *early_wakeup_node_0_base =
    "/sys/devices/platform/19470000.drmdecon/early_wakeup";

typedef enum assignOrderType {
    ORDER_AFBC,
    ORDER_WCG,
    ORDER_AXI,
} assignOrderType_t;

typedef enum DPUblockId {
    DPUF0,
    DPUF1,
    DPU_BLOCK_CNT,
} DPUblockId_t;

const std::unordered_map<DPUblockId_t, String8> DPUBlocks = {
    {DPUF0, String8("DPUF0")},
    {DPUF1, String8("DPUF1")},
};

typedef enum AXIPortId {
    AXI0,
    AXI1,
    AXI_PORT_MAX_CNT,
    AXI_DONT_CARE
} AXIPortId_t;

const std::map<AXIPortId_t, String8> AXIPorts = {
        {AXI0, String8("AXI0")},
        {AXI1, String8("AXI1")},
};

typedef enum ConstraintRev {
    CONSTRAINT_NONE = 0, // don't care
    CONSTRAINT_A0,
    CONSTRAINT_B0
} ConstraintRev_t;

static const dpp_channel_map_t idma_channel_map[] = {
    /* GF physical index is switched to change assign order */
    /* DECON_IDMA is not used */
    {MPP_DPP_GFS,     0, IDMA(0),   IDMA(0)},
    {MPP_DPP_VGRFS,   0, IDMA(1),   IDMA(1)},
    {MPP_DPP_GFS,     1, IDMA(2),   IDMA(2)},
    {MPP_DPP_VGRFS,   1, IDMA(3),   IDMA(3)},
    {MPP_DPP_GFS,     2, IDMA(4),   IDMA(4)},
    {MPP_DPP_VGRFS,   2, IDMA(5),   IDMA(5)},
    {MPP_DPP_GFS,     3, IDMA(6),   IDMA(6)},
    {MPP_DPP_GFS,     4, IDMA(7),   IDMA(7)},
    {MPP_DPP_VGRFS,   3, IDMA(8),   IDMA(8)},
    {MPP_DPP_GFS,     5, IDMA(9),   IDMA(9)},
    {MPP_DPP_VGRFS,   4, IDMA(10),  IDMA(10)},
    {MPP_DPP_GFS,     6, IDMA(11),  IDMA(11)},
    {MPP_DPP_VGRFS,   5, IDMA(12),  IDMA(12)},
    {MPP_DPP_GFS,     7, IDMA(13),  IDMA(13)},
    {MPP_P_TYPE_MAX,  0, ODMA_WB,   IDMA(14)}, // not idma but..
    {static_cast<mpp_phycal_type_t>(MAX_DECON_DMA_TYPE), 0, MAX_DECON_DMA_TYPE,
        IDMA(15)}
};

static const exynos_mpp_t available_otf_mpp_units[] = {
    // Zuma has 8 Graphics-Only Layers
    // Zuma has 6 Video-Graphics Layers
    // Zuma has total 14 Layers

    // DPP0(IDMA_GFS0) in DPUF0 is connected with AXI0 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS0", 0, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI0)},
    // DPP1(IDMA_VGRFS0) in DPUF0 is connected with AXI0 port
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS0", 0, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI0)},
    // DPP2(IDMA_GFS1) in DPUF0 is connected with AXI0 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS1", 1, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI0)},
    // DPP3(IDMA_VGRFS1) in DPUF0 is connected with AXI0 port
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS1", 1, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI0)},

    // DPP4(IDMA_GFS2) in DPUF0 is connected with AXI1 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS2", 2, 0, HWC_RESERVE_DISPLAY_MINOR_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI1)},
    // DPP5(IDMA_VGRFS2) in DPUF0 is connected with AXI1 port
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS2", 2, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI1)},
    // DPP6(IDMA_GFS3) in DPUF0 is connected with AXI1 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS3", 3, 0, HWC_RESERVE_DISPLAY_MINOR_BIT,
     static_cast<uint32_t>(DPUF0), static_cast<uint32_t>(AXI1)},

    // DPP7(IDMA_GFS4) in DPUF1 is connected with AXI1 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS4", 4, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI1)},
    // DPP8(IDMA_VGRFS3) in DPUF1 is connected with AXI1 port
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS3", 3, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI1)},
    // DPP9(IDMA_GFS5) in DPUF1 is connected with AXI1 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS5", 5, 0, HWC_RESERVE_DISPLAY_MINOR_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI1)},
    // DPP10(IDMA_VGRFS4) in DPUF1 is connected with AXI1 port
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS4", 4, 0, HWC_RESERVE_DISPLAY_MAIN_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI1)},

    // DPP11(IDMA_GFS6) in DPUF1 is connected with AXI0 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS6", 6, 0, HWC_RESERVE_DISPLAY_MINOR_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI0)},
    // DPP12(IDMA_VGRFS5) in DPUF1 is connected with AXI0 port
    {MPP_DPP_VGRFS, MPP_LOGICAL_DPP_VGRFS, "DPP_VGRFS5", 5, 0, HWC_RESERVE_DISPLAY_MINOR_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI0)},
    // DPP13(IDMA_GFS7) in DPUF1 is connected with AXI0 port
    {MPP_DPP_GFS, MPP_LOGICAL_DPP_GFS, "DPP_GFS7", 7, 0, HWC_RESERVE_DISPLAY_MINOR_BIT,
     static_cast<uint32_t>(DPUF1), static_cast<uint32_t>(AXI0)},
};

static const std::array<exynos_display_t, 3> AVAILABLE_DISPLAY_UNITS = {{
    {HWC_DISPLAY_PRIMARY, 0, "PrimaryDisplay", "/dev/dri/card0", ""},
    {HWC_DISPLAY_PRIMARY, 1, "SecondaryDisplay", "/dev/dri/card0", ""},
    {HWC_DISPLAY_EXTERNAL, 0, "ExternalDisplay", "/dev/dri/card0", ""}
}};

/*
 * Note :
 * When External or Virtual display is connected,
 * Primary amount = total - others
 */
class HWResourceIndexes {
    private:
        tdm_attr_t attr;
        DPUblockId_t DPUBlockNo;
        AXIPortId_t axiId;
        ConstraintRev_t constraintRev;

    public:
        HWResourceIndexes(const tdm_attr_t& _attr, const DPUblockId_t& _DPUBlockNo,
                          const AXIPortId_t& _axiId, const ConstraintRev_t& _constraintRev)
              : attr(_attr),
                DPUBlockNo(_DPUBlockNo),
                axiId(_axiId),
                constraintRev(_constraintRev) {}
        bool operator<(const HWResourceIndexes& rhs) const {
            if (attr != rhs.attr) return attr < rhs.attr;

            if (DPUBlockNo != rhs.DPUBlockNo) return DPUBlockNo < rhs.DPUBlockNo;

            if (axiId != AXI_DONT_CARE && rhs.axiId != AXI_DONT_CARE && axiId != rhs.axiId)
                return axiId < rhs.axiId;

            if (constraintRev != CONSTRAINT_NONE) return constraintRev < rhs.constraintRev;

            return false;
        }
        String8 toString8() const {
            String8 log;
            log.appendFormat("attr=%d,DPUBlockNo=%d,axiId=%d,constraintRev=%d", attr, DPUBlockNo,
                             axiId, constraintRev);
            return log;
        }
};

typedef struct HWResourceAmounts {
    int mainAmount;
    int minorAmount;
    int total;
} HWResourceAmounts_t;

/* Note :
 * When External or Virtual display is connected,
 * Primary amount = total - others */

const std::map<HWResourceIndexes, HWResourceAmounts_t> HWResourceTables = {
    // SRAM
    {HWResourceIndexes(TDM_ATTR_SRAM_AMOUNT, DPUF0, AXI_DONT_CARE, CONSTRAINT_NONE), {50, 30, 80}},
    {HWResourceIndexes(TDM_ATTR_SRAM_AMOUNT, DPUF1, AXI_DONT_CARE, CONSTRAINT_NONE), {50, 30, 80}},
    // SCALE
    {HWResourceIndexes(TDM_ATTR_SCALE, DPUF0, AXI_DONT_CARE, CONSTRAINT_NONE), {2, 0, 2}},
    {HWResourceIndexes(TDM_ATTR_SCALE, DPUF1, AXI_DONT_CARE, CONSTRAINT_NONE), {1, 1, 2}},
    // SBWC
    {HWResourceIndexes(TDM_ATTR_SBWC, DPUF0, AXI_DONT_CARE, CONSTRAINT_NONE), {2, 0, 2}},
    {HWResourceIndexes(TDM_ATTR_SBWC, DPUF1, AXI_DONT_CARE, CONSTRAINT_NONE), {0, 2, 2}},
    // AFBC
    {HWResourceIndexes(TDM_ATTR_AFBC, DPUF0, AXI_DONT_CARE, CONSTRAINT_NONE), {3, 1, 4}},
    {HWResourceIndexes(TDM_ATTR_AFBC, DPUF1, AXI_DONT_CARE, CONSTRAINT_NONE), {1, 3, 4}},
    // ITP
    {HWResourceIndexes(TDM_ATTR_ITP, DPUF0, AXI_DONT_CARE, CONSTRAINT_NONE), {3, 1, 4}},
    {HWResourceIndexes(TDM_ATTR_ITP, DPUF1, AXI_DONT_CARE, CONSTRAINT_NONE), {1, 3, 4}},
    // ROT_90
    {HWResourceIndexes(TDM_ATTR_ROT_90, DPUF0, AXI_DONT_CARE, CONSTRAINT_NONE), {1, 1, 2}},
    {HWResourceIndexes(TDM_ATTR_ROT_90, DPUF1, AXI_DONT_CARE, CONSTRAINT_NONE), {1, 1, 2}},
    // WCG
    {HWResourceIndexes(TDM_ATTR_WCG, DPUF0, AXI_DONT_CARE, CONSTRAINT_A0), {2, 0, 2}},
    {HWResourceIndexes(TDM_ATTR_WCG, DPUF1, AXI_DONT_CARE, CONSTRAINT_A0), {0, 2, 2}},
    {HWResourceIndexes(TDM_ATTR_WCG, DPUF0, AXI0, CONSTRAINT_B0), {2, 0, 2}},
    {HWResourceIndexes(TDM_ATTR_WCG, DPUF0, AXI1, CONSTRAINT_B0), {2, 0, 2}},
    {HWResourceIndexes(TDM_ATTR_WCG, DPUF1, AXI0, CONSTRAINT_B0), {0, 2, 2}},
    {HWResourceIndexes(TDM_ATTR_WCG, DPUF1, AXI1, CONSTRAINT_B0), {0, 2, 2}},
};

typedef enum lbWidthIndex {
    LB_W_8_512,
    LB_W_513_1024,
    LB_W_1025_1536,
    LB_W_1537_2048,
    LB_W_2049_2304,
    LB_W_2305_2560,
    LB_W_2561_3072,
    LB_W_3073_INF,
} lbWidthIndex_t;

typedef struct lbWidthBoundary {
    uint32_t widthDownto;
    uint32_t widthUpto;
} lbWidthBoundary_t;

const std::map<lbWidthIndex_t, lbWidthBoundary_t> LB_WIDTH_INDEX_MAP = {
    {LB_W_8_512,     {8, 512}},
    {LB_W_513_1024,  {513, 1024}},
    {LB_W_1025_1536, {1025, 1536}},
    {LB_W_1537_2048, {1537, 2048}},
    {LB_W_2049_2304, {2049, 2304}},
    {LB_W_2305_2560, {2035, 2560}},
    {LB_W_2561_3072, {2561, 3072}},
    {LB_W_3073_INF,  {3073, 0xffff}},
};

class sramAmountParams {
private:
    tdm_attr_t attr;
    uint32_t formatProperty;
    lbWidthIndex_t widthIndex;

public:
    sramAmountParams(tdm_attr_t _attr, uint32_t _formatProperty, lbWidthIndex_t _widthIndex)
          : attr(_attr), formatProperty(_formatProperty), widthIndex(_widthIndex) {}
    bool operator<(const sramAmountParams& rhs) const {
        if (attr != rhs.attr) return attr < rhs.attr;

        if (formatProperty != rhs.formatProperty) return formatProperty < rhs.formatProperty;

        if (widthIndex != rhs.widthIndex) return widthIndex < rhs.widthIndex;

        return false;
    }
};

enum {
    SBWC_Y = 0,
    SBWC_UV,
    NON_SBWC_Y,
    NON_SBWC_UV,
};

const std::map<sramAmountParams, uint32_t> sramAmountMap = {
    /** Non rotation **/
    /** BIT8 = 32bit format **/
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_8_512),     4},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_513_1024),  4},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_1025_1536), 8},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_1537_2048), 8},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_2049_2304), 12},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_2305_2560), 12},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_2561_3072), 12},
    {sramAmountParams(TDM_ATTR_AFBC, RGB | BIT8, LB_W_3073_INF),  16},

    /** 16bit format **/
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_513_1024),  2},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_1025_1536), 4},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_1537_2048), 4},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_2049_2304), 6},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_2305_2560), 6},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_2561_3072), 6},
    {sramAmountParams(TDM_ATTR_AFBC, RGB, LB_W_3073_INF),  8},

    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_8_512),     1},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_513_1024),  1},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_1025_1536), 1},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_1537_2048), 1},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_2049_2304), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_2305_2560), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_2561_3072), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_Y, LB_W_3073_INF),  2},

    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_513_1024),  2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_1025_1536), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_1537_2048), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_2049_2304), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_2305_2560), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_2561_3072), 2},
    {sramAmountParams(TDM_ATTR_SBWC, SBWC_UV, LB_W_3073_INF),  2},

    /** Rotation **/
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_8_512),     4},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_513_1024),  8},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_1025_1536), 12},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_1537_2048), 16},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_2049_2304), 18},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_2305_2560), 18},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_2561_3072), 18},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT8, LB_W_3073_INF),  18},

    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_513_1024),  4},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_1025_1536), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_1537_2048), 8},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_2049_2304), 10},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_2305_2560), 10},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_2561_3072), 10},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT8, LB_W_3073_INF),  10},

    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_513_1024),  4},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_1025_1536), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_1537_2048), 8},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_2049_2304), 9},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_2305_2560), 9},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_2561_3072), 9},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_Y | BIT10, LB_W_3073_INF),  9},

    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_513_1024),  2},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_1025_1536), 4},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_1537_2048), 4},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_2049_2304), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_2305_2560), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_2561_3072), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, NON_SBWC_UV | BIT10, LB_W_3073_INF),  6},

    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_513_1024),  4},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_1025_1536), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_1537_2048), 8},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_2049_2304), 9},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_2305_2560), 9},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_2561_3072), 9},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_Y, LB_W_3073_INF),  9},

    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_8_512),     2},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_513_1024),  2},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_1025_1536), 4},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_1537_2048), 4},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_2049_2304), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_2305_2560), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_2561_3072), 6},
    {sramAmountParams(TDM_ATTR_ROT_90, SBWC_UV, LB_W_3073_INF),  6},

    {sramAmountParams(TDM_ATTR_ITP, BIT8, LB_W_3073_INF),  2},
    {sramAmountParams(TDM_ATTR_ITP, BIT10, LB_W_3073_INF), 2},

    /* It's meaning like ow,
     * FORMAT_YUV_MASK == has no alpha, FORMAT_RGB_MASK == has alpha */
    {sramAmountParams(TDM_ATTR_SCALE, FORMAT_YUV_MASK, LB_W_3073_INF), 12},
    {sramAmountParams(TDM_ATTR_SCALE, FORMAT_RGB_MASK, LB_W_3073_INF), 16}};
} // namespace zuma

#endif // ANDROID_EXYNOS_HWC_MODULE_ZUMA_H_
