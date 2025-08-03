/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef __GRAPHIC_BASE_EXYNOS_EXIF_H__
#define __GRAPHIC_BASE_EXYNOS_EXIF_H__

#include <ExynosExif.h>

typedef struct {
    int appid;
    char *appData;
    unsigned int dataSize;
} app_info_t;

typedef struct {
    int num_of_appmarker;
    app_info_t *appInfo;
} extra_appinfo_t;

bool UpdateDebugData(char *jpeg, size_t jpeglen, extra_appinfo_t *extra);
void ExtractDebugAttributeInfo(debug_attribute_t *debug, extra_appinfo_t *extra);

#endif /* __GRAPHIC_BASE_EXYNOS_EXIF_H__ */
