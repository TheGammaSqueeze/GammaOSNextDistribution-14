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

#include <drm/samsung_drm.h>
#include "DisplayColorModule.h"

using namespace android;
namespace gs {

template <typename T, typename M>
int32_t convertDqeMatrixDataToDrmMatrix(T &colorMatrix, M &mat, uint32_t dimension) {
    if (colorMatrix.coeffs.size() != (dimension * dimension)) {
        ALOGE("Invalid coeff size(%zu)",
                colorMatrix.coeffs.size());
        return -EINVAL;
    }
    if (colorMatrix.offsets.size() != dimension) {
        ALOGE("Invalid offset size(%zu)",
                colorMatrix.offsets.size());
        return -EINVAL;
    }
    for (uint32_t i = 0; i < (dimension * dimension); i++) {
        mat.coeffs[i] = colorMatrix.coeffs[i];
    }

    for (uint32_t i = 0; i < dimension; i++) {
        mat.offsets[i] = colorMatrix.offsets[i];
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::eotf(const GsInterfaceType::IDpp::EotfData::ConfigType *config,
                                  DrmDevice *drm, uint32_t &blobId) {
    struct hdr_eotf_lut eotfLut;

    if (config == nullptr) {
        ALOGE("no dpp eotf config");
        return -EINVAL;
    }

    if ((config->tf_data.posx.size() != DRM_SAMSUNG_HDR_EOTF_LUT_LEN) ||
        (config->tf_data.posy.size() != DRM_SAMSUNG_HDR_EOTF_LUT_LEN)) {
        ALOGE("%s: eotf pos size (%zu, %zu)", __func__, config->tf_data.posx.size(),
              config->tf_data.posy.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_HDR_EOTF_LUT_LEN; i++) {
        eotfLut.posx[i] = config->tf_data.posx[i];
        eotfLut.posy[i] = config->tf_data.posy[i];
    }
    int ret = drm->CreatePropertyBlob(&eotfLut, sizeof(eotfLut), &blobId);
    if (ret) {
        ALOGE("Failed to create eotf lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::gm(const GsInterfaceType::IDpp::GmData::ConfigType *config,
                                DrmDevice *drm, uint32_t &blobId) {
    int ret = 0;
    struct hdr_gm_data gmMatrix;

    if (config == nullptr) {
        ALOGE("no dpp GM config");
        return -EINVAL;
    }

    if ((ret = convertDqeMatrixDataToDrmMatrix(config->matrix_data, gmMatrix,
                                            DRM_SAMSUNG_HDR_GM_DIMENS)) != NO_ERROR) {
        ALOGE("Failed to convert gm matrix");
        return ret;
    }
    ret = drm->CreatePropertyBlob(&gmMatrix, sizeof(gmMatrix), &blobId);
    if (ret) {
        ALOGE("Failed to create gm matrix blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::dtm(const GsInterfaceType::IDpp::DtmData::ConfigType *config,
                                 DrmDevice *drm, uint32_t &blobId) {
    struct hdr_tm_data tmData;

    if (config == nullptr) {
        ALOGE("no dpp DTM config");
        return -EINVAL;
    }

    if ((config->tf_data.posx.size() != DRM_SAMSUNG_HDR_TM_LUT_LEN) ||
        (config->tf_data.posy.size() != DRM_SAMSUNG_HDR_TM_LUT_LEN)) {
        ALOGE("%s: dtm pos size (%zu, %zu)", __func__, config->tf_data.posx.size(),
              config->tf_data.posy.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_HDR_TM_LUT_LEN; i++) {
        tmData.posx[i] = config->tf_data.posx[i];
        tmData.posy[i] = config->tf_data.posy[i];
    }

    tmData.coeff_r = config->coeff_r;
    tmData.coeff_g = config->coeff_g;
    tmData.coeff_b = config->coeff_b;
    tmData.rng_x_min = config->rng_x_min;
    tmData.rng_x_max = config->rng_x_max;
    tmData.rng_y_min = config->rng_y_min;
    tmData.rng_y_max = config->rng_y_max;

    int ret = drm->CreatePropertyBlob(&tmData, sizeof(tmData), &blobId);
    if (ret) {
        ALOGE("Failed to create tmData blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::oetf(const GsInterfaceType::IDpp::OetfData::ConfigType *config,
                                  DrmDevice *drm, uint32_t &blobId) {
    struct hdr_oetf_lut oetfLut;

    if (config == nullptr) {
        ALOGE("no dpp OETF config");
        return -EINVAL;
    }

    if ((config->tf_data.posx.size() != DRM_SAMSUNG_HDR_OETF_LUT_LEN) ||
        (config->tf_data.posy.size() != DRM_SAMSUNG_HDR_OETF_LUT_LEN)) {
        ALOGE("%s: oetf pos size (%zu, %zu)", __func__, config->tf_data.posx.size(),
              config->tf_data.posy.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_HDR_OETF_LUT_LEN; i++) {
        oetfLut.posx[i] = config->tf_data.posx[i];
        oetfLut.posy[i] = config->tf_data.posy[i];
    }
    int ret = drm->CreatePropertyBlob(&oetfLut, sizeof(oetfLut), &blobId);
    if (ret) {
        ALOGE("Failed to create oetf lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::gammaMatrix(
        const GsInterfaceType::IDqe::DqeMatrixData::ConfigType *config, DrmDevice *drm,
        uint32_t &blobId) {
    int ret = 0;
    struct exynos_matrix gammaMatrix;
    if ((ret = convertDqeMatrixDataToDrmMatrix(config->matrix_data, gammaMatrix,
                                            DRM_SAMSUNG_MATRIX_DIMENS)) != NO_ERROR) {
        ALOGE("Failed to convert gamma matrix");
        return ret;
    }
    ret = drm->CreatePropertyBlob(&gammaMatrix, sizeof(gammaMatrix), &blobId);
    if (ret) {
        ALOGE("Failed to create gamma matrix blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::degamma(const uint64_t drmLutSize,
        const GsInterfaceType::IDqe::DegammaLutData::ConfigType *config, DrmDevice *drm,
        uint32_t &blobId) {
    if (config == nullptr) {
        ALOGE("no degamma config");
        return -EINVAL;
    }
    using ConfigType = typename GsInterfaceType::IDqe::DegammaLutData::ConfigType;
    if (drmLutSize != ConfigType::kLutLen) {
        ALOGE("degamma lut size mismatch");
        return -EINVAL;
    }

    struct drm_color_lut colorLut[ConfigType::kLutLen];
    for (uint32_t i = 0; i < ConfigType::kLutLen; i++) {
        colorLut[i].red = config->values[i];
    }
    int ret = drm->CreatePropertyBlob(colorLut, sizeof(colorLut), &blobId);
    if (ret) {
        ALOGE("Failed to create degamma lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::linearMatrix(
        const GsInterfaceType::IDqe::DqeMatrixData::ConfigType *config, DrmDevice *drm,
        uint32_t &blobId) {
    int ret = 0;
    struct exynos_matrix linear_matrix;
    if ((ret = convertDqeMatrixDataToDrmMatrix(config->matrix_data, linear_matrix,
                                            DRM_SAMSUNG_MATRIX_DIMENS)) != NO_ERROR) {
        ALOGE("Failed to convert linear matrix");
        return ret;
    }
    ret = drm->CreatePropertyBlob(&linear_matrix, sizeof(linear_matrix), &blobId);
    if (ret) {
        ALOGE("Failed to create linear matrix blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::cgc(const GsInterfaceType::IDqe::CgcData::ConfigType *config,
                                 DrmDevice *drm, uint32_t &blobId) {
    struct cgc_lut cgc;
    if (config == nullptr) {
        ALOGE("no CGC config");
        return -EINVAL;
    }

    if ((config->r_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT) ||
        (config->g_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT) ||
        (config->b_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT)) {
        ALOGE("CGC data size is not same (r: %zu, g: %zu: b: %zu)", config->r_values.size(),
              config->g_values.size(), config->b_values.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_CGC_LUT_REG_CNT; i++) {
        cgc.r_values[i] = config->r_values[i];
        cgc.g_values[i] = config->g_values[i];
        cgc.b_values[i] = config->b_values[i];
    }
    int ret = drm->CreatePropertyBlob(&cgc, sizeof(cgc_lut), &blobId);
    if (ret) {
        ALOGE("Failed to create cgc blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::cgcDither(
        const GsInterfaceType::IDqe::DqeControlData::ConfigType *config, DrmDevice *drm,
        uint32_t &blobId) {
    int ret = 0;
    if (config->cgc_dither_override == false) {
        blobId = 0;
        return ret;
    }

    ret = drm->CreatePropertyBlob((void *)&config->cgc_dither_reg, sizeof(config->cgc_dither_reg),
                                  &blobId);
    if (ret) {
        ALOGE("Failed to create disp dither blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::regamma(
        const uint64_t drmLutSize,
        const GsInterfaceType::IDqe::RegammaLutData::ConfigType *config, DrmDevice *drm,
        uint32_t &blobId) {
    if (config == nullptr) {
        ALOGE("no regamma config");
        return -EINVAL;
    }

    using ConfigType = typename GsInterfaceType::IDqe::RegammaLutData::ConfigType;
    if (drmLutSize != ConfigType::kChannelLutLen) {
        ALOGE("gamma lut size mismatch");
        return -EINVAL;
    }

    struct drm_color_lut colorLut[ConfigType::kChannelLutLen];
    for (uint32_t i = 0; i < ConfigType::kChannelLutLen; i++) {
        colorLut[i].red = config->r_values[i];
        colorLut[i].green = config->g_values[i];
        colorLut[i].blue = config->b_values[i];
    }
    int ret = drm->CreatePropertyBlob(colorLut, sizeof(colorLut), &blobId);
    if (ret) {
        ALOGE("Failed to create gamma lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ColorDrmBlobFactory::displayDither(
        const GsInterfaceType::IDqe::DqeControlData::ConfigType *config, DrmDevice *drm,
        uint32_t &blobId) {
    int ret = 0;
    if (config->disp_dither_override == false) {
        blobId = 0;
        return ret;
    }

    ret = drm->CreatePropertyBlob((void *)&config->disp_dither_reg, sizeof(config->disp_dither_reg),
                                  &blobId);
    if (ret) {
        ALOGE("Failed to create disp dither blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

} // namespace gs
