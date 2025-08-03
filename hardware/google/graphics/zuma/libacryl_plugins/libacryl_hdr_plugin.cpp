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
#include <zuma/displaycolor/displaycolor_zuma.h>
#include <hardware/exynos/g2d_hdr_plugin.h>

#define HDR_HDR_CON 0x3000
#define HDR_SFR_LEN 0x800

#define HDR_HDR_CON_NUM 1
#define HDR_EOTF_SCALER_NUM 1
#define HDR_EOTF_LUT_TS_NUM 20 // two 10-bit
#define HDR_EOTF_LUT_VS_NUM 20 //two 16-bit
#define HDR_GM_COEF_NUM   9   // one 16-bit
#define HDR_GM_OFF_NUM    3   // one 14-bit
#define HDR_TM_COEF_NUM  3 // one 12-bit
#define HDR_TM_YMIX_TF_NUM  1  // one 13-bit
#define HDR_TM_YMIX_VF_NUM  1  // one 12-bit
#define HDR_TM_YMIX_SLOPE_NUM 1  // one 14-bit
#define HDR_TM_YMIX_DV_NUM  1   // one 13 bit
#define HDR_TM_LUT_TS_NUM   24 //two 13-bit
#define HDR_TM_LUT_VS_NUM   24 //two 13-bit
#define HDR_OETF_LUT_TS_NUM 24 //two 14-bit
#define HDR_OETF_LUT_VS_NUM 24 //two 10-bit

#define HDR_MOD_CTRL_OFFSET  (0x4)

// These settings are in HDR_HDR_CON
static const uint32_t HDR_ENABLE_HDR = 1 << 0;
static const uint32_t HDR_ENABLE_EOTF = 1 << 16;
static const uint32_t HDR_ENABLE_EOTF_LUT_SFRRAMP = 0 << 17;
static const uint32_t HDR_ENABLE_EOTF_LUT_PQTABLE = 1 << 17;
static const uint32_t HDR_ENABLE_GM   = 1 << 20;
static const uint32_t HDR_ENABLE_TM  = 1 << 24;
static const uint32_t HDR_ENABLE_OETF  = 1 << 28 ;

// Even and odd settings for HDR coefficients.

//Defining offsets for coefficients
#define HDR_EOTF_SCALER_OFFSET   (HDR_MOD_CTRL_OFFSET)
// Undefined register space of 8 bytes after eotf scaler in zuma. Adding that to the next offset
#define HDR_EOTF_LUT_TS_OFFSET   (HDR_EOTF_SCALER_OFFSET + 8 + 4 * HDR_EOTF_SCALER_NUM)
#define HDR_EOTF_LUT_VS_OFFSET   (HDR_EOTF_LUT_TS_OFFSET + 4 * HDR_EOTF_LUT_TS_NUM)
#define HDR_GM_COEF_OFFSET       (HDR_EOTF_LUT_VS_OFFSET + 4 * HDR_EOTF_LUT_VS_NUM)
#define HDR_GM_OFF_OFFSET        (HDR_GM_COEF_OFFSET + 4 * HDR_GM_COEF_NUM)
#define HDR_TM_COEF_OFFSET       (HDR_GM_OFF_OFFSET + 4 * HDR_GM_OFF_NUM)
#define HDR_TM_YMIX_TF_OFFSET    (HDR_TM_COEF_OFFSET + 4 * HDR_TM_COEF_NUM)
#define HDR_TM_YMIX_VF_OFFSET    (HDR_TM_YMIX_TF_OFFSET + 4 * HDR_TM_YMIX_TF_NUM)
#define HDR_TM_YMIX_SLOPE_OFFSET (HDR_TM_YMIX_VF_OFFSET + 4 * HDR_TM_YMIX_VF_NUM)
#define HDR_TM_YMIX_DV_OFFSET    (HDR_TM_YMIX_SLOPE_OFFSET + 4 * HDR_TM_YMIX_SLOPE_NUM)
#define HDR_TM_LUT_TS_OFFSET     (HDR_TM_YMIX_DV_OFFSET + 4 * HDR_TM_YMIX_DV_NUM)
#define HDR_TM_LUT_VS_OFFSET     (HDR_TM_LUT_TS_OFFSET + 4 * HDR_TM_LUT_TS_NUM)
#define HDR_OETF_LUT_TS_OFFSET   (HDR_TM_LUT_VS_OFFSET + 4 * HDR_TM_LUT_VS_NUM)
#define HDR_OETF_LUT_VS_OFFSET   (HDR_OETF_LUT_TS_OFFSET + 4 * HDR_OETF_LUT_TS_NUM)

#define HDR_LAYER_BASE(layer) (HDR_HDR_CON + HDR_SFR_LEN * (layer))

#define HDR_HDR_MOD_CON(layer)   (HDR_LAYER_BASE(layer))
#define HDR_EOTF_SCALER(layer)   (HDR_LAYER_BASE(layer) + HDR_EOTF_SCALER_OFFSET)
#define HDR_EOTF_LUT_TS(layer)   (HDR_LAYER_BASE(layer) + HDR_EOTF_LUT_TS_OFFSET)
#define HDR_EOTF_LUT_VS(layer)   (HDR_LAYER_BASE(layer) + HDR_EOTF_LUT_VS_OFFSET)
#define HDR_GM_COEF(layer)       (HDR_LAYER_BASE(layer) + HDR_GM_COEF_OFFSET)
#define HDR_GM_OFF(layer)        (HDR_LAYER_BASE(layer) + HDR_GM_OFF_OFFSET)
#define HDR_TM_COEF(layer)       (HDR_LAYER_BASE(layer) + HDR_TM_COEF_OFFSET)
#define HDR_TM_YMIX_TF(layer)    (HDR_LAYER_BASE(layer) + HDR_TM_YMIX_TF_OFFSET)
#define HDR_TM_YMIX_VF(layer)    (HDR_LAYER_BASE(layer) + HDR_TM_YMIX_VF_OFFSET)
#define HDR_TM_YMIX_SLOPE(layer) (HDR_LAYER_BASE(layer) + HDR_TM_YMIX_SLOPE_OFFSET)
#define HDR_TM_YMIX_DV(layer)    (HDR_LAYER_BASE(layer) + HDR_TM_YMIX_DV_OFFSET)
#define HDR_TM_LUT_TS(layer)     (HDR_LAYER_BASE(layer) + HDR_TM_LUT_TS_OFFSET)
#define HDR_TM_LUT_VS(layer)     (HDR_LAYER_BASE(layer) + HDR_TM_LUT_VS_OFFSET)
#define HDR_OETF_LUT_TS(layer)   (HDR_LAYER_BASE(layer) + HDR_OETF_LUT_TS_OFFSET)
#define HDR_OETF_LUT_VS(layer)   (HDR_LAYER_BASE(layer) + HDR_OETF_LUT_VS_OFFSET)


#define G2D_LAYER_HDRMODE(i) (0x390 + (i) * 0x100)

#define MAX_LAYER_COUNT 4

#define HDR_LAYER_SFR_COUNT (\
        HDR_HDR_CON_NUM + HDR_EOTF_SCALER_NUM + \
        HDR_EOTF_LUT_TS_NUM + HDR_EOTF_LUT_VS_NUM + HDR_GM_COEF_NUM + \
        HDR_GM_OFF_NUM + HDR_TM_COEF_NUM + HDR_TM_YMIX_TF_NUM + \
        HDR_TM_YMIX_TF_NUM + HDR_TM_YMIX_VF_NUM + HDR_TM_YMIX_SLOPE_NUM + \
        HDR_TM_YMIX_DV_NUM + HDR_TM_LUT_TS_NUM + \
        HDR_TM_LUT_VS_NUM + HDR_OETF_LUT_TS_NUM + HDR_OETF_LUT_VS_NUM \
        )

static const size_t NUM_HDR_COEFFICIENTS = HDR_LAYER_SFR_COUNT * MAX_LAYER_COUNT + 1; // HDR SFR COUNT x LAYER COUNT + COM_CTRL
static const size_t NUM_HDR_MODE_REGS = MAX_LAYER_COUNT;

class G2DHdrCommandWriter: public IG2DHdr10CommandWriter {
    std::bitset<MAX_LAYER_COUNT> mLayerAlphaMap;
    std::array<displaycolor::IDisplayColorZuma::IDpp *, MAX_LAYER_COUNT> mLayerData{};

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

            set_and_get_next_offset(HDR_HDR_MOD_CON(layer), modectl);
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

        void updateTmCoef(const displaycolor::IDisplayColorZuma::IDpp::DtmData::ConfigType &config, uint32_t offset) {
            offset = set_and_get_next_offset(offset, config.coeff_r);
            offset = set_and_get_next_offset(offset, config.coeff_g);
            offset = set_and_get_next_offset(offset, config.coeff_b);
            offset = set_and_get_next_offset(offset, config.ymix_tf);
            offset = set_and_get_next_offset(offset, config.ymix_vf);
            offset = set_and_get_next_offset(offset, config.ymix_dv);
            set_and_get_next_offset(offset, config.ymix_slope);
        }

        void setEotfScalar(const uint16_t eotf_scalar, uint32_t offset) {
            set_and_get_next_offset(offset, eotf_scalar);
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
        mLayerData[index] = reinterpret_cast<displaycolor::IDisplayColorZuma::IDpp *>(data);
        return true;
    }

    virtual struct g2d_commandlist *getCommands() override {
        mCmdList.reset();

        unsigned int i = 0;
        for (auto layer : mLayerData) {
            if (layer) {
                uint32_t modectl = 0;

                // EOTF settings
                if (layer->EotfLut().enable && layer->EotfLut().config != nullptr) {
                    mCmdList.setEotfScalar(layer->EotfLut().config->eotf_scalar,
                                           HDR_EOTF_SCALER(i));

                    if (layer->EotfLut().config->eotf_lut_en) {
                        modectl |= HDR_ENABLE_EOTF_LUT_PQTABLE;
                    } else {
                        modectl |= HDR_ENABLE_EOTF_LUT_SFRRAMP;
                        mCmdList.updateDouble(layer->EotfLut().config->tf_data.posx,
                                              HDR_EOTF_LUT_TS(i));
                        mCmdList.updateDouble(layer->EotfLut().config->tf_data.posy,
                                              HDR_EOTF_LUT_VS(i));
                    }
                    modectl |= HDR_ENABLE_EOTF;
                }

                // GM settings
                if (layer->Gm().enable && layer->Gm().config != nullptr) {
                    mCmdList.updateSingle(layer->Gm().config->matrix_data.coeffs, HDR_GM_COEF(i));
                    mCmdList.updateSingle(layer->Gm().config->matrix_data.offsets, HDR_GM_OFF(i));
                    modectl |= HDR_ENABLE_GM;
                }

                // DTM settings
                if (layer->Dtm().enable && layer->Dtm().config != nullptr) {
                    mCmdList.updateTmCoef(*layer->Dtm().config, HDR_TM_COEF(i));
                    mCmdList.updateDouble(layer->Dtm().config->tf_data.posx, HDR_TM_LUT_TS(i));
                    mCmdList.updateDouble(layer->Dtm().config->tf_data.posy, HDR_TM_LUT_VS(i));
                    modectl |= HDR_ENABLE_TM;
                }

                // OETF settings
                if (layer->OetfLut().enable && layer->OetfLut().config != nullptr) {
                    mCmdList.updateDouble(layer->OetfLut().config->tf_data.posx,
                                          HDR_OETF_LUT_TS(i));
                    mCmdList.updateDouble(layer->OetfLut().config->tf_data.posy,
                                          HDR_OETF_LUT_VS(i));
                    modectl |= HDR_ENABLE_OETF;
                }

                modectl |= HDR_ENABLE_HDR;

                mCmdList.updateLayer(i, mLayerAlphaMap[0], modectl);
            }

            mLayerAlphaMap >>= 1;
            i++;
        }

        // initialize for the next layer metadata configuration
        mLayerAlphaMap.reset();
        mLayerData.fill(nullptr);

        return mCmdList.get();
    }

    virtual void putCommands(struct g2d_commandlist __unused *commands) override {
        assert(commands == &mCommandList);
    }

    virtual bool hasColorFillLayer(void)  override {
        return true;
    };
};

IG2DHdr10CommandWriter *IG2DHdr10CommandWriter::createInstance() {
    return new G2DHdrCommandWriter();
}
