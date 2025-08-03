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

#ifndef _EXYNOSRESOURCEMANAGER_H
#define _EXYNOSRESOURCEMANAGER_H

#include <unordered_map>
#include "ExynosDevice.h"
#include "ExynosDisplay.h"
#include "ExynosHWCHelper.h"
#include "ExynosMPPModule.h"
#include "ExynosResourceRestriction.h"

using namespace android;

class ExynosDevice;
class ExynosDisplay;
class ExynosMPP;

#define ASSIGN_RESOURCE_TRY_COUNT   100

#define MAX_OVERLAY_LAYER_NUM       20

const std::map<mpp_phycal_type_t, uint64_t> sw_feature_table =
{
    {MPP_DPP_G, MPP_ATTR_DIM},
    {MPP_DPP_GF, MPP_ATTR_DIM},
    {MPP_DPP_GFS, MPP_ATTR_DIM},
    {MPP_DPP_VG, MPP_ATTR_DIM},
    {MPP_DPP_VGS, MPP_ATTR_DIM},
    {MPP_DPP_VGF, MPP_ATTR_DIM},
    {MPP_DPP_VGFS, MPP_ATTR_DIM},
    {MPP_DPP_VGRFS, MPP_ATTR_DIM},
};

#ifndef USE_MODULE_DPU_ATTR_MAP
const dpu_attr_map_t dpu_attr_map_table [] =
{
    {DPP_ATTR_AFBC, MPP_ATTR_AFBC},
    {DPP_ATTR_BLOCK, MPP_ATTR_BLOCK_MODE},
    {DPP_ATTR_FLIP, MPP_ATTR_FLIP_H | MPP_ATTR_FLIP_V},
    {DPP_ATTR_ROT, MPP_ATTR_ROT_90},
    {DPP_ATTR_SCALE, MPP_ATTR_SCALE},
    {DPP_ATTR_HDR, MPP_ATTR_HDR10},
    {DPP_ATTR_C_HDR, MPP_ATTR_HDR10},
    {DPP_ATTR_C_HDR10_PLUS, MPP_ATTR_HDR10PLUS},
    {DPP_ATTR_WCG, MPP_ATTR_WCG},
};
#endif

/* Based on multi-resolution feature */
enum dst_realloc_state {
    DST_REALLOC_DONE = 0,
    DST_REALLOC_START,
    DST_REALLOC_GOING,
};

class ExynosMPPVector : public android::SortedVector< ExynosMPP* > {
    public:
        ExynosMPPVector();
        ExynosMPPVector(const ExynosMPPVector& rhs);
        virtual int do_compare(const void* lhs, const void* rhs) const;
};

class ExynosResourceManager {
    private:
    class DstBufMgrThread: public Thread {
        private:
            ExynosResourceManager *mExynosResourceManager;
            Condition mCondition;
        public:
            bool mRunning;
            Mutex mMutex;
            Mutex mStateMutex;
            Mutex mResInfoMutex;
            uint32_t mBufXres;
            uint32_t mBufYres;
            void reallocDstBufs(uint32_t Xres, uint32_t Yres);
            bool needDstRealloc(uint32_t Xres, uint32_t Yres, ExynosMPP *m2mMPP);
            DstBufMgrThread(ExynosResourceManager *exynosResourceManager);
            ~DstBufMgrThread();
            virtual bool threadLoop();
    };

    public:
        uint32_t mForceReallocState;
        ExynosDevice *mDevice;
        bool hasHdrLayer;
        bool hasDrmLayer;
        bool isHdrExternal;

        uint32_t mFormatRestrictionCnt;
        uint32_t mSizeRestrictionCnt[RESTRICTION_MAX];
        restriction_key_t mFormatRestrictions[RESTRICTION_CNT_MAX];
        restriction_size_element_t mSizeRestrictions[RESTRICTION_MAX][RESTRICTION_CNT_MAX];

        std::unordered_map<uint32_t /* physical type */, uint64_t /* attribute */> mMPPAttrs;

        ExynosResourceManager(ExynosDevice *device);
        virtual ~ExynosResourceManager();
        void reloadResourceForHWFC();
        void setTargetDisplayLuminance(uint16_t min, uint16_t max);
        void setTargetDisplayDevice(int device);
        int32_t doPreProcessing();
        void doReallocDstBufs(uint32_t Xres, uint32_t Yres);
        int32_t doAllocDstBufs(uint32_t mXres, uint32_t mYres);
        int32_t assignResource(ExynosDisplay *display);
        int32_t assignResourceInternal(ExynosDisplay *display);
        static ExynosMPP* getExynosMPP(uint32_t type);
        static ExynosMPP* getExynosMPP(uint32_t physicalType, uint32_t physicalIndex);
        static void enableMPP(uint32_t physicalType, uint32_t physicalIndex, uint32_t logicalIndex, uint32_t enable);
        static void setScaleDownRatio(uint32_t physicalType,
                uint32_t physicalIndex, uint32_t logicalIndex,
                uint32_t scaleDownRatio);
        int32_t updateSupportedMPPFlag(ExynosDisplay * display);
        int32_t resetResources();
        int32_t preAssignResources();
        void preAssignWindows(ExynosDisplay *display);
        int32_t preProcessLayer(ExynosDisplay *display);
        int32_t resetAssignedResources(ExynosDisplay *display, bool forceReset = false);
        virtual int32_t assignCompositionTarget(ExynosDisplay *display, uint32_t targetType);
        int32_t validateLayer(uint32_t index, ExynosDisplay *display, ExynosLayer *layer);
        int32_t assignLayers(ExynosDisplay *display, uint32_t priority);
        virtual int32_t otfMppReordering(ExynosDisplay *__unused display,
                                         ExynosMPPVector __unused &otfMPPs,
                                         struct exynos_image __unused &src,
                                         struct exynos_image __unused &dst) {
            return 0;
        }
        virtual int32_t assignLayer(ExynosDisplay *display, ExynosLayer *layer, uint32_t layer_index,
                exynos_image &m2m_out_img, ExynosMPP **m2mMPP, ExynosMPP **otfMPP, uint32_t &overlayInfo);
        virtual int32_t assignWindow(ExynosDisplay *display);
        virtual int32_t checkScenario(ExynosDisplay *display);
        int32_t updateResourceState();
        static float getResourceUsedCapa(ExynosMPP &mpp);
        int32_t updateExynosComposition(ExynosDisplay *display);
        int32_t updateClientComposition(ExynosDisplay *display);
        int32_t getCandidateM2mMPPOutImages(ExynosDisplay *display,
                ExynosLayer *layer, std::vector<exynos_image> &image_lists);
        int32_t setResourcePriority(ExynosDisplay *display);
        int32_t deliverPerformanceInfo();
        int32_t prepareResources(const int32_t willOnDispId = -1);
        int32_t finishAssignResourceWork();
        int32_t initResourcesState(ExynosDisplay *display);

        uint32_t getOtfMPPSize() {return (uint32_t)mOtfMPPs.size();};
        ExynosMPP* getOtfMPP(uint32_t index) {return mOtfMPPs[index];};

        uint32_t getM2mMPPSize() {return (uint32_t)mM2mMPPs.size();};
        ExynosMPP* getM2mMPP(uint32_t index) {return mM2mMPPs[index];};

        virtual void makeAcrylRestrictions(mpp_phycal_type_t type);
        void makeSizeRestrictions(uint32_t mppId, const restriction_size_t &size,
                                  restriction_classification_t format);
        void makeFormatRestrictions(restriction_key_t table);

        void updateRestrictions();

        mpp_phycal_type_t getPhysicalType(int ch) const;
        ExynosMPP* getOtfMPPWithChannel(int ch);
        uint32_t getFeatureTableSize() const;
        const static ExynosMPPVector& getOtfMPPs() { return mOtfMPPs; };
        float getM2MCapa(uint32_t physicalType);
        virtual bool hasHDR10PlusMPP();
        float getAssignedCapacity(uint32_t physicalType);

        void dump(String8 &result) const;
        void setM2MCapa(uint32_t physicalType, uint32_t capa);
        virtual bool isAssignable(ExynosMPP* candidateMPP, ExynosDisplay* display,
                                  struct exynos_image& src, struct exynos_image& dst,
                                  ExynosMPPSource* mppSrc);

    private:
        int32_t changeLayerFromClientToDevice(ExynosDisplay* display, ExynosLayer* layer,
                                              uint32_t layer_index, const exynos_image& m2m_out_img,
                                              ExynosMPP* m2mMPP, ExynosMPP* otfMPP);
        void dump(const restriction_classification_t, String8 &result) const;

        sp<DstBufMgrThread> mDstBufMgrThread;

    protected:
        virtual void setFrameRateForPerformance(ExynosMPP &mpp, AcrylicPerformanceRequestFrame *frame);
        void getCandidateScalingM2mMPPOutImages(const ExynosDisplay *display,
                                                const exynos_image &src_img,
                                                const exynos_image &dst_img,
                                                std::vector<exynos_image> &image_lists);
        exynos_image getAlignedImage(exynos_image image, const ExynosMPP *m2mMpp,
                                     const ExynosMPP *otfMpp) const;
        int32_t validateRCDLayer(const ExynosDisplay &display, const ExynosLayer &layer,
                                 const uint32_t layerIndex, const exynos_image &srcImg,
                                 const exynos_image &dstImg);
        static ExynosMPPVector mOtfMPPs;
        static ExynosMPPVector mM2mMPPs;
        uint32_t mResourceReserved; /* Set MPP logical type for bit operation */
        float mMinimumSdrDimRatio;

        android::Vector<ExynosDisplay *> mDisplays;
        std::map<uint32_t, ExynosDisplay *> mDisplayMap;

    protected:
        bool mDeviceSupportWCG = false;

    public:
        void initDisplays(android::Vector<ExynosDisplay *> displays,
                          std::map<uint32_t, ExynosDisplay *> displayMap) {
            mDisplays = displays;
            mDisplayMap = displayMap;
        }
        ExynosDisplay *getDisplay(uint32_t displayId) { return mDisplayMap[displayId]; }

        virtual void updateSupportWCG();
        virtual bool deviceSupportWCG() { return mDeviceSupportWCG; }

        /* return 1 if it's needed */
        bool needHdrProcessing(ExynosDisplay *display, exynos_image &srcImg, exynos_image &dstImg);
        uint32_t needHWResource(ExynosDisplay *display, exynos_image &srcImg, exynos_image &dstImg,
                                tdm_attr_t attr);

        /* TDM (Time-Division Multiplexing) based Resource Management */
        virtual bool isHWResourceAvailable(ExynosDisplay __unused *display,
                                           ExynosMPP __unused *currentMPP,
                                           ExynosMPPSource __unused *mppSrc) {
            return true;
        }
        virtual uint32_t setDisplaysTDMInfo(__unused ExynosDisplay* mainDisp,
                                            __unused ExynosDisplay* minorDisp) {
            return 0;
        }
        virtual uint32_t calculateHWResourceAmount(ExynosDisplay __unused *display,
                                                   ExynosMPPSource __unused *mppSrc) {
            return 0;
        }

        std::pair<ExynosDisplay*, ExynosDisplay*> decideMainAndMinorDisplay(
                const int32_t willOnDispId) const;
        void updatePreAssignDisplayList(const ExynosDisplay* mainDisp,
                                        const ExynosDisplay* minorDisp);
};

#endif //_EXYNOSRESOURCEMANAGER_H
