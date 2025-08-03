/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __HARDWARE_EXYNOS_HW2DCOMPOSITOR_G2D_H__
#define __HARDWARE_EXYNOS_HW2DCOMPOSITOR_G2D_H__

#include <memory>

#include <hardware/exynos/acryl.h>

#include <hardware/exynos/g2d_hdr_plugin.h>

#include <uapi/g2d.h>

#include "acrylic_internal.h"
#include "acrylic_device.h"

class G2DHdrWriter {
    std::unique_ptr<IG2DHdr10CommandWriter> mWriter;
    g2d_commandlist *mCmds = nullptr;
public:
    G2DHdrWriter() {
#ifdef LIBACRYL_G2D_HDR_PLUGIN
        mWriter.reset(IG2DHdr10CommandWriter::createInstance());
#endif
    }

    ~G2DHdrWriter() {
        putCommands();
    }

    bool setLayerStaticMetadata(int layer_index, int dataspace, unsigned int min_luminance, unsigned int max_luminance) {
        return mWriter ? mWriter->setLayerStaticMetadata(layer_index, dataspace, min_luminance, max_luminance) : true;
    }

    bool setLayerImageInfo(int layer_index, unsigned int pixfmt, bool alpha_premult) {
        return mWriter ? mWriter->setLayerImageInfo(layer_index, pixfmt, alpha_premult) : true;
    }

    void setLayerOpaqueData(int layer_index, void *data, size_t len) {
        if (mWriter)
            mWriter->setLayerOpaqueData(layer_index, data, len);
    }

    bool setTargetInfo(int dataspace, void *data) {
        return mWriter ? mWriter->setTargetInfo(dataspace, data) : true;
    }

    void setTargetDisplayLuminance(unsigned int min, unsigned int max) {
        if (mWriter)
		mWriter->setTargetDisplayLuminance(min, max);
    }

    void getLayerHdrMode(g2d_task &task) {
        if (!mCmds)
            return;

        for (unsigned int i = 0; i < mCmds->layer_count; i++) {
            unsigned int idx;

            if (mWriter->hasColorFillLayer())
                idx = (mCmds->layer_hdr_mode[i].offset >> 8) - 3;
            else
                idx = (mCmds->layer_hdr_mode[i].offset >> 8) - 2;

            // If premultiplied alpha values are de-premultied before HDR conversion,
            // it should be multiplied again after the conversion. But some of the HDR processors
            // does not have functionality of alpha multiplicaion after the conversion even though
            // it has demultipier before the conversion.
            // If the HDR process is lack of alpha multiplication, multiplication of alpha value
            // should be performed by G2D.
            if (mCmds->layer_hdr_mode[i].value & G2D_LAYER_HDRMODE_DEMULT_ALPHA)
                task.commands.source[idx][G2DSFR_SRC_COMMAND] |= G2D_LAYERCMD_PREMULT_ALPHA;
            task.commands.source[idx][G2DSFR_SRC_HDRMODE] = mCmds->layer_hdr_mode[i].value;
        }
    }

    unsigned int getCommandCount() {
        return mCmds ? mCmds->command_count : 0;
    }

    unsigned int write(g2d_reg *regs) {
        if (mCmds) {
            memcpy(regs, mCmds->commands, sizeof(*regs) * mCmds->command_count);
            return mCmds->command_count;
        }

        return 0;
    }

    void getCommands() {
        if (!mCmds && mWriter)
            mCmds = mWriter->getCommands();
    }

    void putCommands() {
        if (mWriter && mCmds) {
            mWriter->putCommands(mCmds);
            mCmds = nullptr;
        }
    }
};

struct g2d_fmt;

class AcrylicCompositorG2D: public Acrylic {
public:
    AcrylicCompositorG2D(const HW2DCapability &capability, bool newcolormode);
    virtual ~AcrylicCompositorG2D();
    virtual bool execute(int fence[], unsigned int num_fences);
    virtual bool execute(int *handle = NULL);
    virtual bool waitExecution(int handle);
    virtual unsigned int getLaptimeUSec() { return mTask.laptime_in_usec; }
    /*
     * Return -1 on failure in configuring the give priority or the priority is invalid.
     * Return 0 when the priority is configured successfully without any side effect.
     * Return 1 when the priority is configured successfully but the priority may not
     * be reflected promptly due to other pending tasks with lower priorities.
     */
    virtual int prioritize(int priority = -1);
    virtual bool requestPerformanceQoS(AcrylicPerformanceRequest *request);
private:
    int ioctlG2D(void);
    bool executeG2D(int fence[], unsigned int num_fences, bool nonblocking);
    bool prepareImage(AcrylicCanvas &layer, struct g2d_layer &image, uint32_t cmd[], int index);
    bool prepareSource(AcrylicLayer &layer, struct g2d_layer &image, uint32_t cmd[], hw2d_coord_t target_size,
                       unsigned int index, unsigned int image_index);
    bool prepareSolidLayer(AcrylicCanvas &canvas, struct g2d_layer &image, uint32_t cmd[]);
    bool prepareSolidLayer(AcrylicLayer &layer, struct g2d_layer &image, uint32_t cmd[], hw2d_coord_t target_size, unsigned int index);
    bool reallocLayer(unsigned int layercount);
    unsigned int updateFilterCoefficients(unsigned int layercount, g2d_reg regs[]);

    AcrylicDevice mDev;
    g2d_task	  mTask;
    G2DHdrWriter  mHdrWriter;
    unsigned int  mMaxSourceCount;
    int mPriority;
    unsigned int mVersion;
    bool mUsePolyPhaseFilter;

    g2d_fmt *halfmt_to_g2dfmt_tbl;
    size_t len_halfmt_to_g2dfmt_tbl;
};

#endif //__HARDWARE_EXYNOS_HW2DCOMPOSITOR_G2D_H__
