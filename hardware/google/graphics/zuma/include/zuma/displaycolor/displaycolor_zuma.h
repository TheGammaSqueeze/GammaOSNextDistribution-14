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

#ifndef DISPLAYCOLOR_ZUMA_H_
#define DISPLAYCOLOR_ZUMA_H_

#include <displaycolor/displaycolor_gs101.h>

namespace displaycolor {

/// An interface for accessing ZUMA color management data.
class IDisplayColorZuma : public IDisplayColorGeneric {
   public:
    /// Interface for accessing data for DPP stages.
    struct IDppData {
        struct IEotfData {
           private:
            struct EotfConfigType
                : public IDisplayColorGS101::FlexLutConfigType<uint16_t, uint16_t, 39> {
                // True to use built-in PQ table, false to use tf_data
                bool eotf_lut_en;
                uint16_t eotf_scalar;
            };

           public:
            /// Register data for the EOTF LUT in DPP.
            using EotfData = DisplayStage<EotfConfigType>;

            /// Get data for the EOTF LUT.
            virtual const EotfData& EotfLut() const = 0;
            virtual ~IEotfData() {}
        };

        struct IGmData {
            /// Register data for the gamut mapping (GM) matrix in DPP.
            using GmData = DisplayStage<IDisplayColorGS101::MatrixConfigType<uint16_t, 3>>;

            /// Get data for the gamut mapping (GM) matrix.
            virtual const GmData& Gm() const = 0;
            virtual ~IGmData() {}
        };

        struct IDtmData {
           private:
            struct Rgb2YData {
                uint16_t coeff_r;
                uint16_t coeff_g;
                uint16_t coeff_b;
                uint16_t ymix_tf;
                uint16_t ymix_vf;
                uint16_t ymix_dv;
                uint16_t ymix_slope;
            };
            struct DtmConfigType
                : public IDisplayColorGS101::FlexLutConfigType<uint16_t, uint16_t, 48>,
                  public Rgb2YData {};

           public:
            using DtmData = DisplayStage<DtmConfigType>;

            /// Get data for DTM stage
            virtual const DtmData& Dtm() const = 0;
            virtual ~IDtmData() {}
        };

        struct IOetfData {
            /// Register data for the OETF LUT in DPP.
            using OetfData =
                DisplayStage<IDisplayColorGS101::FlexLutConfigType<uint16_t, uint16_t, 48>>;

            /// Get data for the OETF LUT.
            virtual const OetfData& OetfLut() const = 0;
            virtual ~IOetfData() {}
        };
    };

    struct IDpp
        : public IStageDataCollection<IDppData::IEotfData, IDppData::IGmData,
                                      IDppData::IDtmData, IDppData::IOetfData> {
        /// Get the solid color
        virtual const Color SolidColor() const = 0;

        virtual ~IDpp() {}
    };

    /// Interface for accessing data for DQE stages.
    struct IDqeData {
     public:
        struct IDegammaLutData {
           private:
            struct DegammaConfigType {
                using XContainer = uint16_t;
                using YContainer = uint16_t;
                static constexpr size_t kLutLen = 33;

                IDisplayColorGS101::TransferFunctionData<XContainer, YContainer, kLutLen> values;
            };

           public:
            /// Register data for the degamma LUT in DQE.
            using DegammaLutData = DisplayStage<DegammaConfigType>;
            /// Get data for the 1D de-gamma LUT (EOTF).
            virtual const DegammaLutData& DegammaLut() const = 0;
            virtual ~IDegammaLutData() {}
        };

        struct IRegammaLutData {
           private:
            struct RegammaConfigType {
                using XContainer = uint16_t;
                using YContainer = uint16_t;
                static constexpr size_t kChannelLutLen = 33;

                IDisplayColorGS101::TransferFunctionData<XContainer, YContainer, kChannelLutLen>
                    r_values;
                IDisplayColorGS101::TransferFunctionData<XContainer, YContainer, kChannelLutLen>
                    g_values;
                IDisplayColorGS101::TransferFunctionData<XContainer, YContainer, kChannelLutLen>
                    b_values;
            };

           public:
            /// Register data for the regamma LUT.
            using RegammaLutData = DisplayStage<RegammaConfigType>;

            /// Get data for the 3x1D re-gamma LUTa (OETF).
            virtual const RegammaLutData& RegammaLut() const = 0;
            virtual ~IRegammaLutData() {}
        };
    };

    using IPanel = IDisplayColorGS101::IPanel;

    struct IDqe : public IStageDataCollection<
                          IDisplayColorGS101::IDqeData::IDqeControlData,
                          IDisplayColorGS101::IDqeData::IGammaMatrixData,
                          IDqeData::IDegammaLutData,
                          IDisplayColorGS101::IDqeData::ILinearMatrixData,
                          IDisplayColorGS101::IDqeData::ICgcData,
                          IDqeData::IRegammaLutData> {
        virtual ~IDqe() {}
    };

    /// Interface for accessing particular display color data
    struct IDisplayPipelineData {
        /**
         * @brief Get handles to Display Pre-Processor (DPP) data accessors.
         *
         * The order of the returned DPP handles match the order of the
         * LayerColorData provided as part of struct DisplayScene and
         * IDisplayColorGeneric::Update().
         */
        virtual std::vector<std::reference_wrapper<const IDpp>> Dpp() const = 0;

        /// Get a handle to Display Quality Enhancer (DQE) data accessors.
        virtual const IDqe& Dqe() const = 0;

        /// Get a handle to panel data accessors
        virtual const IPanel& Panel() const = 0;

        virtual ~IDisplayPipelineData() {}
    };

    /// Get pipeline color data for specified display type
    virtual const IDisplayPipelineData* GetPipelineData(DisplayType display) const = 0;

    virtual ~IDisplayColorZuma() {}
};

extern "C" {

/// Get the ZUMA instance.
IDisplayColorZuma* GetDisplayColorZuma(const std::vector<DisplayInfo> &display_info);
}

}  // namespace displaycolor

#endif  // DISPLAYCOLOR_ZUMA_H_
