/*
 *  libacryl_plugins/libacryl_hdr_plugin.cpp
 *
 *   Copyright 2020 Samsung Electronics Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
#include <cassert>
#include <array>

#include <gs101/displaycolor/displaycolor_gs101.h>
#include <hardware/exynos/g2d_hdr_plugin.h>

#define HDR_BASE 0x3000
#define HDR_SFR_LEN 0x800

#define HDR_COM_CTRL 0x3004 // [0] HDR enable
static const uint32_t VAL_HDR_CTRL_ENABLE = 1;

#define HDR_MOD_CTRL_NUM  1   // [0] oetf enable, [1] eotf enable [2] gm enable [5] tm enable
#define HDR_OETF_POSX_NUM 17  // two 16-bit from LSB
#define HDR_OETF_POSY_NUM 17  // two 10-bit from LSB
#define HDR_EOTF_POSX_NUM 65  // two 10-bit from LSB
#define HDR_EOTF_POSY_NUM 129 // one 16-bit
#define HDR_GM_COEF_NUM   9   // one 19-bit
#define HDR_GM_OFF_NUM    3   // one 17-bit
#define HDR_TM_COEF_NUM   1   // three 10-bit from LSB (R, G, B)
#define HDR_TM_RNGX_NUM   1   // two 16-bit from LSB (min:max)
#define HDR_TM_RNGY_NUM   1   // two 9-bit from LSB (min:max)
#define HDR_TM_POSX_NUM   17  // two 16-bit from LSB
#define HDR_TM_POSY_NUM   33  // one 27-bit from LSB

#define HDR_MOD_CTRL_OFFSET  (0x8)
static const uint32_t HDR_ENABLE_OETF = 1 << 0;
static const uint32_t HDR_ENABLE_EOTF = 1 << 1;
static const uint32_t HDR_ENABLE_GM   = 1 << 2;
static const uint32_t HDR_ENABLE_DTM  = 1 << 5;

#define HDR_OETF_POSX_OFFSET (HDR_MOD_CTRL_OFFSET  + 4 * HDR_MOD_CTRL_NUM )
#define HDR_OETF_POSY_OFFSET (HDR_OETF_POSX_OFFSET + 4 * HDR_OETF_POSX_NUM)
#define HDR_EOTF_POSX_OFFSET (HDR_OETF_POSY_OFFSET + 4 * HDR_OETF_POSY_NUM)
#define HDR_EOTF_POSY_OFFSET (HDR_EOTF_POSX_OFFSET + 4 * HDR_EOTF_POSX_NUM)
#define HDR_GM_COEF_OFFSET   (HDR_EOTF_POSY_OFFSET + 4 * HDR_EOTF_POSY_NUM)
#define HDR_GM_OFF_OFFSET    (HDR_GM_COEF_OFFSET   + 4 * HDR_GM_COEF_NUM  )
#define HDR_TM_COEF_OFFSET   (HDR_GM_OFF_OFFSET    + 4 * HDR_GM_OFF_NUM   )
#define HDR_TM_RNGX_OFFSET   (HDR_TM_COEF_OFFSET   + 4 * HDR_TM_COEF_NUM  )
#define HDR_TM_RNGY_OFFSET   (HDR_TM_RNGX_OFFSET   + 4 * HDR_TM_RNGX_NUM  )
#define HDR_TM_POSX_OFFSET   (HDR_TM_RNGY_OFFSET   + 4 * HDR_TM_RNGY_NUM  )
#define HDR_TM_POSY_OFFSET   (HDR_TM_POSX_OFFSET   + 4 * HDR_TM_POSX_NUM  )

#define HDR_LAYER_BASE(layer) (HDR_BASE + HDR_SFR_LEN * (layer))

#define HDR_MOD_CTRL(layer)  (HDR_LAYER_BASE(layer) + HDR_MOD_CTRL_OFFSET )
#define HDR_OETF_POSX(layer) (HDR_LAYER_BASE(layer) + HDR_OETF_POSX_OFFSET)
#define HDR_OETF_POSY(layer) (HDR_LAYER_BASE(layer) + HDR_OETF_POSY_OFFSET)
#define HDR_EOTF_POSX(layer) (HDR_LAYER_BASE(layer) + HDR_EOTF_POSX_OFFSET)
#define HDR_EOTF_POSY(layer) (HDR_LAYER_BASE(layer) + HDR_EOTF_POSY_OFFSET)
#define HDR_GM_COEF(layer)   (HDR_LAYER_BASE(layer) + HDR_GM_COEF_OFFSET  )
#define HDR_GM_OFF(layer)    (HDR_LAYER_BASE(layer) + HDR_GM_OFF_OFFSET   )
#define HDR_TM_COEF(layer)   (HDR_LAYER_BASE(layer) + HDR_TM_COEF_OFFSET  )
#define HDR_TM_RNGX(layer)   (HDR_LAYER_BASE(layer) + HDR_TM_RNGX_OFFSET  )
#define HDR_TM_RNGY(layer)   (HDR_LAYER_BASE(layer) + HDR_TM_RNGY_OFFSET  )
#define HDR_TM_POSX(layer)   (HDR_LAYER_BASE(layer) + HDR_TM_POSX_OFFSET  )
#define HDR_TM_POSY(layer)   (HDR_LAYER_BASE(layer) + HDR_TM_POSY_OFFSET  )

#define G2D_LAYER_HDRMODE(i) (0x290 + (i) * 0x100)

#define MAX_LAYER_COUNT 4
#define HDR_LAYER_SFR_COUNT (\
      HDR_MOD_CTRL_NUM + HDR_OETF_POSX_NUM + HDR_OETF_POSY_NUM + \
      HDR_EOTF_POSX_NUM + HDR_EOTF_POSY_NUM + HDR_GM_COEF_NUM + \
      HDR_GM_OFF_NUM + HDR_TM_COEF_NUM + HDR_TM_RNGX_NUM + \
      HDR_TM_RNGY_NUM + HDR_TM_POSX_NUM + HDR_TM_POSY_NUM\
      )

static const size_t NUM_HDR_COEFFICIENTS = HDR_LAYER_SFR_COUNT * MAX_LAYER_COUNT + 1; // HDR SFR COUNT x LAYER COUNT + COM_CTRL
static const size_t NUM_HDR_MODE_REGS = MAX_LAYER_COUNT;

class G2DHdrCommandWriter: public IG2DHdr10CommandWriter {
    std::bitset<MAX_LAYER_COUNT> mLayerAlphaMap;
    std::array<displaycolor::IDisplayColorGS101::IDpp *, MAX_LAYER_COUNT> mLayerData{};

public:
    struct CommandList {
        std::array<g2d_reg, NUM_HDR_COEFFICIENTS> commands;     // (294 * 4 + 1) * 8 bytes
        std::array<g2d_reg, NUM_HDR_MODE_REGS> layer_hdr_modes; // 4 * 8 bytes
        g2d_commandlist cmdlist{};

        CommandList() {
            cmdlist.commands = commands.data();
            cmdlist.layer_hdr_mode = layer_hdr_modes.data();
        }

        ~CommandList() { }

        void reset() {
            cmdlist.command_count = 0;
            cmdlist.layer_count = 0;
        }

        g2d_commandlist *get() { return &cmdlist; }

        uint32_t set_and_get_next_offset(uint32_t offset, uint32_t value) {
            commands[cmdlist.command_count].offset = offset;
            commands[cmdlist.command_count].value = value;
            cmdlist.command_count++;
            return offset + sizeof(value);
        }

        void updateLayer(std::size_t layer, bool alpha_premultiplied, uint32_t modectl) {
            auto &hdr_mode = layer_hdr_modes[cmdlist.layer_count++];

            hdr_mode.offset = G2D_LAYER_HDRMODE(layer);
            hdr_mode.value = layer;
            // The premultiplied alpha should be demultiplied before HDR conversion.
            if (alpha_premultiplied)
                hdr_mode.value |= G2D_LAYER_HDRMODE_DEMULT_ALPHA;

            set_and_get_next_offset(HDR_MOD_CTRL(layer), modectl);
        }

        template <typename containerT>
        void updateDouble(const containerT &container, uint32_t offset) {
            for (std::size_t n = 0; n < container.size(); n += 2)
                offset = set_and_get_next_offset(offset, container[n] | container[n + 1] << 16);
            if ((container.size() % 2) == 1)
                set_and_get_next_offset(offset, container.back());
        }

        template <typename containerT>
        void updateSingle(const containerT &container, uint32_t offset) {
            for (auto item : container)
                offset = set_and_get_next_offset(offset, item);
        }

        void updateTmCoef(const displaycolor::IDisplayColorGS101::IDpp::DtmData::ConfigType &config, uint32_t offset) {
            offset = set_and_get_next_offset(offset, config.coeff_r | (config.coeff_g << 10) | (config.coeff_b << 20));
            offset = set_and_get_next_offset(offset, config.rng_x_min | (config.rng_x_max << 16));
            set_and_get_next_offset(offset, config.rng_y_min | (config.rng_y_max << 16));
        }

        void updateHdr() {
            if (cmdlist.command_count > 0)
                set_and_get_next_offset(HDR_COM_CTRL, VAL_HDR_CTRL_ENABLE);
        }
    } mCmdList;

    G2DHdrCommandWriter() { }
    virtual ~G2DHdrCommandWriter() { }

    virtual bool setLayerStaticMetadata(int __unused index, int __unused dataspace,
                                unsigned int __unused min_luminance, unsigned int __unused max_luminance) override {
        return true;
    }

    virtual bool setLayerImageInfo(int index, unsigned int __unused pixfmt, bool alpha_premult) override {
        if (alpha_premult)
            mLayerAlphaMap.set(index);
        return true;
    }

    virtual bool setTargetInfo(int __unused dataspace, void * __unused data) override {
        return true;
    }

    virtual bool setLayerOpaqueData(int index, void *data, size_t __unused len) override {
        mLayerData[index] = reinterpret_cast<displaycolor::IDisplayColorGS101::IDpp *>(data);
        return true;
    }

    virtual struct g2d_commandlist *getCommands() override {
        mCmdList.reset();

        unsigned int i = 0;
        for (auto layer : mLayerData) {
            if (layer) {
                uint32_t modectl = 0;

                if (layer->EotfLut().enable && layer->EotfLut().config != nullptr) {
                    mCmdList.updateDouble(layer->EotfLut().config->tf_data.posx, HDR_EOTF_POSX(i));
                    mCmdList.updateSingle(layer->EotfLut().config->tf_data.posy, HDR_EOTF_POSY(i));
                    modectl |= HDR_ENABLE_EOTF;
                }

                if (layer->Gm().enable && layer->Gm().config != nullptr) {
                    mCmdList.updateSingle(layer->Gm().config->matrix_data.coeffs, HDR_GM_COEF(i));
                    mCmdList.updateSingle(layer->Gm().config->matrix_data.offsets, HDR_GM_OFF(i));
                    modectl |= HDR_ENABLE_GM;
                }

                if (layer->Dtm().enable && layer->Dtm().config != nullptr) {
                    mCmdList.updateTmCoef(*layer->Dtm().config, HDR_TM_COEF(i));
                    mCmdList.updateDouble(layer->Dtm().config->tf_data.posx, HDR_TM_POSX(i));
                    mCmdList.updateSingle(layer->Dtm().config->tf_data.posy, HDR_TM_POSY(i));
                    modectl |= HDR_ENABLE_DTM;
                }

                if (layer->OetfLut().enable && layer->OetfLut().config != nullptr) {
                    mCmdList.updateDouble(layer->OetfLut().config->tf_data.posx, HDR_OETF_POSX(i));
                    mCmdList.updateDouble(layer->OetfLut().config->tf_data.posy, HDR_OETF_POSY(i));
                    modectl |= HDR_ENABLE_OETF;
                }

                mCmdList.updateLayer(i, mLayerAlphaMap[0], modectl);
            }

            mLayerAlphaMap >>= 1;
            i++;
        }

        mCmdList.updateHdr();

        // initialize for the next layer metadata configuration
        mLayerAlphaMap.reset();
        mLayerData.fill(nullptr);

        return mCmdList.get();
    }

    virtual void putCommands(struct g2d_commandlist __unused *commands) override {
        assert(commands == &mCommandList);
    }
};

IG2DHdr10CommandWriter *IG2DHdr10CommandWriter::createInstance() {
    return new G2DHdrCommandWriter();
}
