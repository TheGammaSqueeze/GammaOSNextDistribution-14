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

#pragma once

#include <gs101/displaycolor/displaycolor_gs101.h>
#include "DisplayColorLoader.h"
#include "drmdevice.h"

namespace gs {

static constexpr char kGsEntry[] = "GetDisplayColorGS101";
class ColorDrmBlobFactory {
 public:
     using GsInterfaceType = displaycolor::IDisplayColorGS101;
     using DcLoaderType = DisplayColorLoader<GsInterfaceType, kGsEntry>;

     static int32_t eotf(const GsInterfaceType::IDpp::EotfData::ConfigType *config,
                         android::DrmDevice *drm, uint32_t &blobId);
     static int32_t gm(const GsInterfaceType::IDpp::GmData::ConfigType *config,
                       android::DrmDevice *drm, uint32_t &blobId);
     static int32_t dtm(const GsInterfaceType::IDpp::DtmData::ConfigType *config,
                        android::DrmDevice *drm, uint32_t &blobId);
     static int32_t oetf(const GsInterfaceType::IDpp::OetfData::ConfigType *config,
                         android::DrmDevice *drm, uint32_t &blobId);
     static int32_t gammaMatrix(const GsInterfaceType::IDqe::DqeMatrixData::ConfigType *config,
                                android::DrmDevice *drm, uint32_t &blobId);
     static int32_t degamma(const uint64_t drmLutSize,
                            const GsInterfaceType::IDqe::DegammaLutData::ConfigType *config,
                            android::DrmDevice *drm, uint32_t &blobId);
     static int32_t linearMatrix(const GsInterfaceType::IDqe::DqeMatrixData::ConfigType *config,
                                 android::DrmDevice *drm, uint32_t &blobId);
     static int32_t cgc(const GsInterfaceType::IDqe::CgcData::ConfigType *config,
                        android::DrmDevice *drm, uint32_t &blobId);
     static int32_t cgcDither(const GsInterfaceType::IDqe::DqeControlData::ConfigType *config,
                              android::DrmDevice *drm, uint32_t &blobId);
     static int32_t regamma(const uint64_t drmLutSize,
                            const GsInterfaceType::IDqe::RegammaLutData::ConfigType *config,
                            android::DrmDevice *drm, uint32_t &blobId);
     static int32_t displayDither(const GsInterfaceType::IDqe::DqeControlData::ConfigType *config,
                                  android::DrmDevice *drm, uint32_t &blobId);
};

} // namespace gs
