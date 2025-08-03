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

/**
 * Project HWC 2.0 Design
 */

#ifndef _EXYNOSMPP_H
#define _EXYNOSMPP_H

#include <utils/Thread.h>
#include <utils/Mutex.h>
#include <utils/Condition.h>
#include <utils/String8.h>
#include <utils/StrongPointer.h>
#include <utils/List.h>
#include <utils/Vector.h>
#include <map>
#include <hardware/exynos/acryl.h>
#include <map>
#include "ExynosHWCModule.h"
#include "ExynosHWCHelper.h"
#include "ExynosMPPType.h"

class ExynosDisplay;
class ExynosMPP;
class ExynosResourceManager;

#ifndef NUM_MPP_DST_BUFS
#define NUM_MPP_DST_BUFS_DEFAULT 3
/*
 * Buffer number can be different according to type
 * #define NUM_MPP_DST_BUFS(type) ((type == MPP_LOGICAL_G2D_RGB) ? 3:2)
 */
#define NUM_MPP_DST_BUFS(type) (3)
#endif

#ifndef G2D_MAX_SRC_NUM
#define G2D_MAX_SRC_NUM 15
#endif

#define G2D_JUSTIFIED_DST_ALIGN     16

#define NUM_MPP_SRC_BUFS G2D_MAX_SRC_NUM

#ifndef G2D_RESTRICTIVE_SRC_NUM
#define G2D_RESTRICTIVE_SRC_NUM   5
#endif

#ifndef G2D_BASE_PPC
#define G2D_BASE_PPC 2.8
#endif
#ifndef G2D_DST_BASE_PPC
#define G2D_DST_BASE_PPC 3.5
#endif
#ifndef G2D_DST_BASE_PPC_YUV420
#define G2D_DST_BASE_PPC_YUV420 2.8
#endif
#ifndef G2D_DST_BASE_PPC_ROT
#define G2D_DST_BASE_PPC_ROT  2.4
#endif
#ifndef G2D_BASE_PPC_COLORFILL
#define G2D_BASE_PPC_COLORFILL  3.8
#endif

#ifndef G2D_CLOCK
#define G2D_CLOCK   711000
#endif

#ifndef MSC_CLOCK
#define MSC_CLOCK   534000
#endif

#ifndef VPP_CLOCK
#define VPP_CLOCK 664000
#endif
#ifndef VPP_MIC_FACTOR
#define VPP_MIC_FACTOR  2
#endif

#ifndef VPP_TE_PERIOD
#define VPP_TE_PERIOD 63
#endif
#ifndef VPP_MARGIN
#define VPP_MARGIN 1.1
#endif
#ifndef VPP_BUBBLE
#define VPP_BUBBLE 0.06
#endif

#define VPP_RESOL_CLOCK_FACTOR (VPP_TE_PERIOD * VPP_MARGIN)
#define VPP_RESOL_MARGIN (VPP_MARGIN + VPP_BUBBLE)
#ifndef VPP_DISP_FACTOR
#define VPP_DISP_FACTOR 1.0
#endif
#ifndef VPP_PIXEL_PER_CLOCK
#define VPP_PIXEL_PER_CLOCK 2
#endif

#ifndef MPP_G2D_CAPACITY
#define MPP_G2D_CAPACITY    8
#endif
// G2D or MSC additional margin capacity when HDR layer is passed.
#ifndef MPP_HDR_MARGIN
#define MPP_HDR_MARGIN 1.2
#endif

#ifndef MPP_MSC_CAPACITY
#define MPP_MSC_CAPACITY    8
#endif

/* Currently allowed capacity percentage is over 10% */
#define MPP_CAPA_OVER_THRESHOLD 1.1

#ifndef MPP_G2D_SRC_SCALED_WEIGHT
#define MPP_G2D_SRC_SCALED_WEIGHT   1.125
#endif

#ifndef MPP_G2D_DST_ROT_WEIGHT
#define MPP_G2D_DST_ROT_WEIGHT  2.0
#endif

#define MPP_DUMP_PATH  "/data/vendor/log/hwc/output.dat"

using namespace android;

enum {
    eMPPSaveCapability            =     1ULL << 0,
    eMPPStrideCrop                =     1ULL << 1,
    eMPPUnsupportedRotation       =     1ULL << 2,
    eMPPHWBusy                    =     1ULL << 3,
    eMPPExeedSrcCropMax           =     1ULL << 4,
    eMPPUnsupportedColorTransform =     1ULL << 5,
    eMPPUnsupportedBlending       =     1ULL << 6,
    eMPPUnsupportedFormat         =     1ULL << 7,
    eMPPNotAlignedDstSize         =     1ULL << 8,
    eMPPNotAlignedSrcCropPosition =     1ULL << 9,
    eMPPNotAlignedHStride         =     1ULL << 10,
    eMPPNotAlignedVStride         =     1ULL << 11,
    eMPPExceedHStrideMaximum      =     1ULL << 12,
    eMPPExceedVStrideMaximum      =     1ULL << 13,
    eMPPExeedMaxDownScale         =     1ULL << 14,
    eMPPExeedMaxDstWidth          =     1ULL << 15,
    eMPPExeedMaxDstHeight         =     1ULL << 16,
    eMPPExeedMinSrcWidth          =     1ULL << 17,
    eMPPExeedMinSrcHeight         =     1ULL << 18,
    eMPPExeedMaxUpScale           =     1ULL << 19,
    eMPPExeedSrcWCropMax          =     1ULL << 20,
    eMPPExeedSrcHCropMax          =     1ULL << 21,
    eMPPExeedSrcWCropMin          =     1ULL << 22,
    eMPPExeedSrcHCropMin          =     1ULL << 23,
    eMPPNotAlignedCrop            =     1ULL << 24,
    eMPPNotAlignedOffset          =     1ULL << 25,
    eMPPExeedMinDstWidth          =     1ULL << 26,
    eMPPExeedMinDstHeight         =     1ULL << 27,
    eMPPUnsupportedCompression    =     1ULL << 28,
    eMPPUnsupportedCSC            =     1ULL << 29,
    eMPPUnsupportedDIMLayer       =     1ULL << 30,
    eMPPUnsupportedDRM            =     1ULL << 31,
    eMPPUnsupportedDynamicMeta    =     1ULL << 32,
    eMPPSatisfiedRestriction      =     1ULL << 33,
    eMPPExeedHWResource           =     1ULL << 34,
};

enum {
    MPP_TYPE_NONE,
    MPP_TYPE_OTF,
    MPP_TYPE_M2M
};

enum {
    MPP_ASSIGN_STATE_FREE       =     0x00000000,
    MPP_ASSIGN_STATE_RESERVED   =     0x00000001,
    MPP_ASSIGN_STATE_ASSIGNED   =     0x00000002,
};

enum {
    MPP_HW_STATE_IDLE,
    MPP_HW_STATE_RUNNING
};

enum {
    MPP_BUFFER_NORMAL = 0,
    MPP_BUFFER_NORMAL_DRM,
    MPP_BUFFER_SECURE_DRM,
    MPP_BUFFER_DUMP,
};

enum {
    MPP_MEM_MMAP = 1,
    MPP_MEM_USERPTR,
    MPP_MEM_OVERLAY,
    MPP_MEM_DMABUF,
};

enum {
    MPP_SOURCE_COMPOSITION_TARGET,
    MPP_SOURCE_LAYER,
    MPP_SOURCE_MAX
};

/* Based on multi-resolution feature */
typedef enum {
    DST_SIZE_HD = 0,
    DST_SIZE_HD_PLUS,
    DST_SIZE_FHD,
    DST_SIZE_FHD_PLUS,
    DST_SIZE_WQHD,
    DST_SIZE_WQHD_PLUS,
    DST_SIZE_UNKNOWN,
} dst_alloc_buf_size_t;

#ifndef DEFAULT_MPP_DST_FORMAT
#define DEFAULT_MPP_DST_FORMAT HAL_PIXEL_FORMAT_RGBA_8888
#endif

/* TODO: Switch back to single-fd format, tracked in b/261356480 */
#ifndef DEFAULT_MPP_DST_YUV_FORMAT
#define DEFAULT_MPP_DST_YUV_FORMAT HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M
#endif
#ifndef DEFAULT_MPP_DST_UNCOMP_YUV_FORMAT
#define DEFAULT_MPP_DST_UNCOMP_YUV_FORMAT HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M
#endif

typedef struct exynos_mpp_img_info {
    buffer_handle_t bufferHandle;
    uint32_t bufferType;
    uint32_t format;
    android_dataspace_t dataspace;
    AcrylicLayer *mppLayer;
    int acrylicAcquireFenceFd;
    int acrylicReleaseFenceFd;
} exynos_mpp_img_info_t;

typedef enum {
    PPC_SCALE_NO = 0,   /* no scale */
    PPC_SCALE_DOWN_1_4, /* x1/1.xx ~ x1/4 */
    PPC_SCALE_DOWN_4_9, /* x1/4 ~ x1/9 */
    PPC_SCALE_DOWN_9_16, /* x1/9 ~ x1/16 */
    PPC_SCALE_DOWN_16_,  /* x1/16 ~ */
    PPC_SCALE_UP_1_4,   /* x1.xx ~ x4 */
    PPC_SCALE_UP_4_,    /* x4 ~ */
    PPC_SCALE_MAX
} scaling_index_t;

typedef enum {
    PPC_FORMAT_YUV420   =   0,
    PPC_FORMAT_YUV422,
    PPC_FORMAT_RGB32,
    PPC_FORMAT_SBWC,
    PPC_FORMAT_P010,
    PPC_FORMAT_AFBC_RGB,
    PPC_FORMAT_AFBC_YUV,
    PPC_FORMAT_FORMAT_MAX
} format_index_t;

typedef enum {
    PPC_ROT_NO   =   0,
    PPC_ROT,
    PPC_ROT_MAX
} rot_index_t;

typedef struct ppc_list_for_scaling {
    float ppcList[PPC_SCALE_MAX];
} ppc_list_for_scaling_t;

typedef std::map<uint32_t, ppc_list_for_scaling> ppc_table;

typedef struct dstMetaInfo {
    uint16_t minLuminance = 0;
    uint16_t maxLuminance = 0;
} dstMetaInfo_t;

enum
{
    NODE_NONE,
    NODE_SRC,
    NODE_DST
}; /* nodeType */

enum
{
    HAL_TRANSFORM_NONE = 0
};

enum
{
    HAL_PIXEL_FORMAT_NONE = 0
};

#define YUV_CHROMA_H_SUBSAMPLE  static_cast<uint32_t>(2) // Horizontal
#define YUV_CHROMA_V_SUBSAMPLE  static_cast<uint32_t>(2) // Vertical
#define RESTRICTION_CNT_MAX 1024

typedef enum restriction_classification {
    RESTRICTION_RGB =   0,
    RESTRICTION_YUV,
    RESTRICTION_MAX
} restriction_classification_t;

typedef struct restriction_key
{
    mpp_phycal_type_t hwType; /* MPP_DPP_VG, MPP_DPP_VGFS, ... */
    uint32_t nodeType; /* src or dst */
    uint32_t format; /* HAL format */
    uint32_t reserved;
} restriction_key_t;

typedef struct restriction_size
{
    static constexpr size_t kNumofRestriction = 16;
    union {
        std::array<uint32_t, kNumofRestriction> mData;
        struct {
            uint32_t maxDownScale;
            uint32_t maxUpScale;
            uint32_t maxFullWidth;
            uint32_t maxFullHeight;
            uint32_t minFullWidth;
            uint32_t minFullHeight;
            uint32_t fullWidthAlign;
            uint32_t fullHeightAlign;
            uint32_t maxCropWidth;
            uint32_t maxCropHeight;
            uint32_t minCropWidth;
            uint32_t minCropHeight;
            uint32_t cropXAlign;
            uint32_t cropYAlign;
            uint32_t cropWidthAlign;
            uint32_t cropHeightAlign;
        };
    };

    restriction_size() = default;
    restriction_size(std::array<uint32_t, kNumofRestriction> &&rdata) : mData(rdata){};

    bool operator==(const restriction_size &rhs) const { return rhs.mData == mData; }
} restriction_size_t;

typedef struct restriction_size_element
{
    restriction_key_t key;
    restriction_size_t sizeRestriction;
} restriction_size_element_t;

typedef struct restriction_table_element
{
    uint32_t classfication_type;
    const restriction_size_element *table;
    uint32_t table_element_size;
} restriction_table_element_t;
/* */

#define FORMAT_SHIFT   10
#define ROT_SHIFT   20
#define PPC_IDX(x,y,z) (x|(y<<FORMAT_SHIFT)|(z<<ROT_SHIFT))

const std::map<uint32_t, int32_t> dataspace_standard_map =
{
    {HAL_DATASPACE_STANDARD_UNSPECIFIED,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT709,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT601_625,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT601_525,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT2020,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_BT470M,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_FILM,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_DCI_P3,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_STANDARD_ADOBE_RGB,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS}
};

const std::map<uint32_t, int32_t> dataspace_transfer_map =
{
    {HAL_DATASPACE_TRANSFER_UNSPECIFIED,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_LINEAR,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_SRGB,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_SMPTE_170M,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_GAMMA2_2,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_GAMMA2_6,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_GAMMA2_8,
        MPP_ATTR_WCG | MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_ST2084,
        MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS},

    {HAL_DATASPACE_TRANSFER_HLG,
        MPP_ATTR_HDR10 | MPP_ATTR_HDR10PLUS}
};

void dumpExynosMPPImgInfo(uint32_t type, exynos_mpp_img_info &imgInfo);

struct ExynosMPPFrameInfo
{
    uint32_t srcNum;
    exynos_image srcInfo[NUM_MPP_SRC_BUFS];
    exynos_image dstInfo[NUM_MPP_SRC_BUFS];
};

class ExynosMPPSource {
    public:
        ExynosMPPSource();
        ExynosMPPSource(uint32_t sourceType, void *source);
        ~ExynosMPPSource(){};
        void setExynosImage(const exynos_image& src_img, const exynos_image& dst_img);
        void setExynosMidImage(const exynos_image& mid_img);

        uint32_t mSourceType;
        void *mSource;
        exynos_image mSrcImg;
        exynos_image mDstImg;
        exynos_image mMidImg;

        ExynosMPP *mOtfMPP;
        ExynosMPP *mM2mMPP;

        /**
         * SRAM/HW resource info
         */
        std::unordered_map<tdm_attr_t, int32_t> mHWResourceAmount;
        uint32_t getHWResourceAmount(tdm_attr_t attr) { return mHWResourceAmount[attr]; }

        uint32_t setHWResourceAmount(tdm_attr_t attr, uint32_t amount) {
            mHWResourceAmount[attr] = amount;
            return 0;
        }

        bool mNeedPreblending = false;
};

bool exynosMPPSourceComp(const ExynosMPPSource* l, const ExynosMPPSource* r);
void dump(const restriction_size_t &restrictionSize, String8 &result);

class ExynosMPP {
private:
    class ResourceManageThread: public Thread {
        private:
            ExynosMPP *mExynosMPP;
            Condition mCondition;
            List<exynos_mpp_img_info > mFreedBuffers;
            List<int> mStateFences;

            void freeBuffers();
            bool checkStateFences();
        public:
            bool mRunning;
            Mutex mMutex;
            ResourceManageThread(ExynosMPP *exynosMPP);
            ~ResourceManageThread();
            virtual bool threadLoop();
            void addFreedBuffer(exynos_mpp_img_info freedBuffer);
            void addStateFence(int fence);
    };

public:
    ExynosResourceManager *mResourceManager;
    /**
     * Resource type
     * Ex: MPP_DPP_VGFS, MPP_DPP_VG, MPP_MSC, MPP_G2D
     */
    uint32_t mMPPType;
    uint32_t  mPhysicalType;
    uint32_t  mLogicalType;
    String8 mName;
    uint32_t mPhysicalIndex;
    uint32_t mLogicalIndex;
    uint32_t mPreAssignDisplayInfo;
    uint32_t mPreAssignDisplayList[DISPLAY_MODE_NUM];
    static int mainDisplayWidth;
    static int mainDisplayHeight;

    uint32_t mHWState;
    int mLastStateFenceFd;
    uint32_t mAssignedState;
    bool    mEnable;

    ExynosDisplay *mAssignedDisplay;

    /* Some resource can support blending feature
     * then source can be multiple layers */
    Vector <ExynosMPPSource* > mAssignedSources;
    uint32_t mMaxSrcLayerNum;

    uint32_t mPrevAssignedState;
    int32_t mPrevAssignedDisplayType;
    int32_t mReservedDisplay;

    android::sp<ResourceManageThread> mResourceManageThread;
    float mCapacity;
    float mUsedCapacity;

    union {
        struct {
            float mUsedBaseCycles;
            uint32_t mRotatedSrcCropBW;
            uint32_t mNoRotatedSrcCropBW;
        };
    };

    bool mAllocOutBufFlag;
    bool mFreeOutBufFlag;
    bool mHWBusyFlag;
    /* For reuse previous frame */
    ExynosMPPFrameInfo mPrevFrameInfo;
    struct exynos_mpp_img_info mSrcImgs[NUM_MPP_SRC_BUFS];
    struct exynos_mpp_img_info mDstImgs[NUM_MPP_DST_BUFS_DEFAULT];
    int32_t mCurrentDstBuf;
    int32_t mPrivDstBuf;
    bool mNeedCompressedTarget;
    struct restriction_size mSrcSizeRestrictions[RESTRICTION_MAX];
    struct restriction_size mDstSizeRestrictions[RESTRICTION_MAX];

    // Force Dst buffer reallocation
    dst_alloc_buf_size_t mDstAllocatedSize;

    /* For libacryl */
    Acrylic *mAcrylicHandle;

    bool mUseM2MSrcFence;
    /* MPP's attribute bit (supported feature bit) */
    uint64_t    mAttr;

    uint32_t mAssignOrder;
    uint32_t mAXIPortId;
    uint32_t mHWBlockId;

    bool mNeedSolidColorLayer;

    ExynosMPP(ExynosResourceManager* resourceManager,
            uint32_t physicalType, uint32_t logicalType, const char *name,
            uint32_t physicalIndex, uint32_t logicalIndex, uint32_t preAssignInfo);
    virtual ~ExynosMPP();

    int32_t allocOutBuf(uint32_t w, uint32_t h, uint32_t format, uint64_t usage, uint32_t index);
    int32_t setOutBuf(buffer_handle_t outbuf, int32_t fence);
    int32_t freeOutBuf(exynos_mpp_img_info dst);
    int32_t doPostProcessing(struct exynos_image& dst);
    int32_t setupRestriction();
    int32_t getSrcReleaseFence(uint32_t srcIndex);
    int32_t resetSrcReleaseFence();
    int32_t getDstImageInfo(exynos_image *img);
    int32_t setDstAcquireFence(int releaseFence);
    int32_t resetDstReleaseFence();
    int32_t requestHWStateChange(uint32_t state);
    int32_t setHWStateFence(int32_t fence);
    virtual int64_t isSupported(ExynosDisplay &display, struct exynos_image &src, struct exynos_image &dst);

    bool isDataspaceSupportedByMPP(struct exynos_image &src, struct exynos_image &dst);
    bool isSupportedHDR(struct exynos_image &src, struct exynos_image &dst);
    bool isSupportedBlend(struct exynos_image &src);
    virtual bool isSupportedTransform(struct exynos_image &src);
    bool isSupportedCapability(ExynosDisplay &display, struct exynos_image &src);
    bool isSupportedDRM(struct exynos_image &src);
    virtual bool isSupportedHStrideCrop(struct exynos_image &src);
    bool checkDownscaleCap(const float resolution, const float displayRatio_V) const;
    virtual bool checkSpecificRestriction(const uint32_t __unused refreshRate,
                                          const struct exynos_image __unused &src,
                                          const struct exynos_image __unused &dst) {
        return false;
    }
    virtual uint32_t getDownscaleRestriction(const struct exynos_image &src,
                                             const struct exynos_image &dst) const;
    virtual uint32_t getMaxDownscale(const ExynosDisplay &display, const struct exynos_image &src,
                                     const struct exynos_image &dst) const;
    virtual uint32_t getMaxUpscale(const struct exynos_image &src,
                                   const struct exynos_image &dst) const;
    uint32_t getSrcMaxWidth(struct exynos_image &src);
    uint32_t getSrcMaxHeight(struct exynos_image &src);
    uint32_t getSrcMinWidth(struct exynos_image &src);
    uint32_t getSrcMinWidth(uint32_t idx);
    uint32_t getSrcMinHeight(struct exynos_image &src);
    uint32_t getSrcMinHeight(uint32_t idx);
    uint32_t getSrcWidthAlign(struct exynos_image &src);
    uint32_t getSrcHeightAlign(struct exynos_image &src);
    uint32_t getSrcMaxCropWidth(struct exynos_image &src);
    uint32_t getSrcMaxCropHeight(struct exynos_image &src);
    virtual uint32_t getSrcMaxCropSize(struct exynos_image &src);
    uint32_t getSrcMinCropWidth(struct exynos_image &src);
    uint32_t getSrcMinCropHeight(struct exynos_image &src);
    virtual uint32_t getSrcXOffsetAlign(struct exynos_image &src);
    uint32_t getSrcXOffsetAlign(uint32_t idx);
    uint32_t getSrcYOffsetAlign(struct exynos_image &src);
    uint32_t getSrcYOffsetAlign(uint32_t idx);
    uint32_t getSrcCropWidthAlign(const struct exynos_image &src) const;
    uint32_t getSrcCropWidthAlign(uint32_t idx) const;
    uint32_t getSrcCropHeightAlign(const struct exynos_image &src) const;
    uint32_t getSrcCropHeightAlign(uint32_t idx) const;
    virtual bool isSrcFormatSupported(struct exynos_image &src);
    virtual bool isDimLayerSupported();
    int32_t isSupportThis(uint32_t mSupportAttr);

    uint32_t getDstMaxWidth(struct exynos_image &dst);
    uint32_t getDstMaxHeight(struct exynos_image &dst);
    uint32_t getDstMinWidth(struct exynos_image &dst);
    uint32_t getDstMinHeight(struct exynos_image &dst);
    virtual uint32_t getDstWidthAlign(const struct exynos_image &dst) const;
    uint32_t getDstHeightAlign(const struct exynos_image &dst) const;
    uint32_t getDstXOffsetAlign(struct exynos_image &dst);
    uint32_t getDstYOffsetAlign(struct exynos_image &dst);
    uint32_t getOutBufAlign();
    virtual bool isDstFormatSupported(struct exynos_image &dst);
    int32_t isSupportLayerColorTransform(
            struct exynos_image &src, struct exynos_image &dst);
    uint32_t getSrcMaxBlendingNum(struct exynos_image &src, struct exynos_image &dst);
    uint32_t getAssignedSourceNum();

    /* Based on multi-resolution support */
    void setDstAllocSize(uint32_t width, uint32_t height);
    dst_alloc_buf_size_t getDstAllocSize();
    virtual bool needPreAllocation();

    int32_t resetMPP();
    int32_t resetAssignedState();
    int32_t resetAssignedState(ExynosMPPSource *mppSource);
    int32_t reserveMPP(int32_t displayType = -1);

    bool isAssignableState(ExynosDisplay *display, struct exynos_image &src, struct exynos_image &dst);
    bool isAssignable(ExynosDisplay *display, struct exynos_image &src, struct exynos_image &dst,
                      float totalUsedCapacity);
    int32_t assignMPP(ExynosDisplay *display, ExynosMPPSource* mppSource);

    bool hasEnoughCapa(ExynosDisplay *display, struct exynos_image &src, struct exynos_image &dst,
                       float totalUsedCapa);
    float getRequiredCapacity(ExynosDisplay *display, struct exynos_image &src, struct exynos_image &dst);
    int32_t updateUsedCapacity();
    void resetUsedCapacity();
    int prioritize(int priority);

    uint32_t getMPPClockKhz() { return mClockKhz; };

    void dump(String8& result);
    uint32_t increaseDstBuffIndex();
    bool canSkipProcessing();

    virtual bool isSupportedCompression(struct exynos_image &src);

    void closeFences();

    void reloadResourceForHWFC();
    void setTargetDisplayLuminance(uint16_t min, uint16_t max);
    void setTargetDisplayDevice(int device);

    virtual bool checkRotationCondition(struct exynos_image &src);
    void updateAttr();
    void updatePreassignedDisplay(uint32_t fromDisplayBit, uint32_t toDisplayBit);
    dstMetaInfo getDstMetaInfo(android_dataspace_t dstDataspace);
    float getAssignedCapacity();

    void setPPC(float ppc) { mPPC = ppc; };
    void setClockKhz(uint32_t clock) { mClockKhz = clock; };

    virtual void initTDMInfo(uint32_t hwBlockIndex, uint32_t axiPortIndex) {
        mHWBlockId = hwBlockIndex;
        mAXIPortId = axiPortIndex;
    }
    virtual uint32_t getHWBlockId() { return mHWBlockId; }
    virtual uint32_t getAXIPortId() { return mAXIPortId; }

protected:
    uint32_t getBufferType(uint64_t usage);
    uint32_t getBufferType(const buffer_handle_t handle);
    uint64_t getBufferUsage(uint64_t usage);
    bool needCompressDstBuf() const;
    bool needDstBufRealloc(struct exynos_image &dst, uint32_t index);
    bool canUsePrevFrame();
    int32_t setupDst(exynos_mpp_img_info *dstImgInfo);
    virtual int32_t doPostProcessingInternal();
    virtual int32_t setupLayer(exynos_mpp_img_info *srcImgInfo,
            struct exynos_image &src, struct exynos_image &dst);
    virtual int32_t setColorConversionInfo() { return NO_ERROR; };

    uint32_t getRestrictionClassification(const struct exynos_image &img) const;

    /*
     * getPPC for src, dst referencing mppSources in mAssignedSources and
     * assignCheckSrc, assignCheckDst that are likely to be added to the mAssignedSources
     */
    float getPPC(const struct exynos_image &src, const struct exynos_image &dst,
            const struct exynos_image &criteria,
            const struct exynos_image *assignCheckSrc = NULL,
            const struct exynos_image *assignCheckDst = NULL);
    float getPPC() { return mPPC; };

    /* format and rotation index are defined by indexImage */
    void getPPCIndex(const struct exynos_image &indexImage,
            const struct exynos_image &refImage,
            uint32_t &formatIndex, uint32_t &rotIndex, uint32_t &scaleIndex,
            const struct exynos_image &criteria);

    float getRequiredBaseCycles(struct exynos_image &src, struct exynos_image &dst);
    bool addCapacity(ExynosMPPSource* mppSource);
    bool removeCapacity(ExynosMPPSource* mppSource);
    /*
     * This should be called by isCSCSupportedByMPP()
     * This function checks additional restriction for color space conversion
     */
    virtual bool checkCSCRestriction(struct exynos_image &src, struct exynos_image &dst);

    /*
     * Check additional conditions those have a capacity exception.
     */
    virtual bool isCapacityExceptionCondition(float totalUsedCapacity, float requiredCapacity,
                                              struct exynos_image &src);

    uint32_t mClockKhz = 0;
    float mPPC = 0;
};

#endif //_EXYNOSMPP_H
