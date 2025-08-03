/*
 * Copyright (C) 2020 Arm Limited.
 *
 * Copyright 2016 The Android Open Source Project
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

#ifndef GRALLOC_COMMON_SHARED_METADATA_H
#define GRALLOC_COMMON_SHARED_METADATA_H

#include <optional>
#include <vector>
#include "gralloctypes/Gralloc4.h"
#include "mali_gralloc_buffer.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "gralloc_helper.h"

#include "SharedMetadata_struct.h"
#include "pixel-gralloc/metadata.h"

namespace arm
{
namespace mapper
{
namespace common
{

using android::hardware::hidl_vec;
using aidl::android::hardware::graphics::common::Rect;
using aidl::android::hardware::graphics::common::Smpte2086;
using aidl::android::hardware::graphics::common::Cta861_3;
using aidl::android::hardware::graphics::common::BlendMode;
using aidl::android::hardware::graphics::common::Dataspace;
using ::pixel::graphics::VideoGMV;

void shared_metadata_init(void *memory, std::string_view name);
size_t shared_metadata_size();

void get_name(const private_handle_t *hnd, std::string *name);

void get_crop_rect(const private_handle_t *hnd, std::optional<Rect> *crop);
android::status_t set_crop_rect(const private_handle_t *hnd, const Rect &crop_rectangle);

void get_dataspace(const private_handle_t *hnd, std::optional<Dataspace> *dataspace);
void set_dataspace(const private_handle_t *hnd, const Dataspace &dataspace);

void get_blend_mode(const private_handle_t *hnd, std::optional<BlendMode> *blend_mode);
void set_blend_mode(const private_handle_t *hnd, const BlendMode &blend_mode);

void get_smpte2086(const private_handle_t *hnd, std::optional<Smpte2086> *smpte2086);
android::status_t set_smpte2086(const private_handle_t *hnd, const std::optional<Smpte2086> &smpte2086);

void get_cta861_3(const private_handle_t *hnd, std::optional<Cta861_3> *cta861_3);
android::status_t set_cta861_3(const private_handle_t *hnd, const std::optional<Cta861_3> &cta861_3);

void get_smpte2094_40(const private_handle_t *hnd, std::optional<std::vector<uint8_t>> *smpte2094_40);
android::status_t set_smpte2094_40(const private_handle_t *hnd, const std::optional<std::vector<uint8_t>> &smpte2094_40);

void* get_video_hdr(const private_handle_t *hnd);

void* get_video_roiinfo(const private_handle_t *hnd);

VideoGMV get_video_gmv(const private_handle_t *hnd);
android::status_t set_video_gmv(const private_handle_t *hnd, const VideoGMV &data);

} // namespace common
} // namespace mapper
} // namespace arm

#endif
