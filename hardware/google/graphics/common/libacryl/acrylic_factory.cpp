/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2016 The Android Open Source Project
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

#include <log/log.h>
#include <mali_gralloc_formats.h>
#include <exynos_format.h> // hardware/smasung_slsi/exynos/include

#include <cstring>

#include "acrylic_g2d.h"
#include "acrylic_internal.h"
#include "acrylic_capability.h"

Acrylic *Acrylic::createInstance(const char *spec)
{
    Acrylic *compositor = nullptr;

    ALOGD_TEST("Creating a new Acrylic instance of '%s'", spec);
    compositor = createAcrylicCompositorG2D(spec);
    if (compositor) {
        ALOGI("%s compositor added", spec);
    }

    ALOGE_IF(!compositor, "Failed to create HW2D compositor of '%s'", spec);

    return compositor;
}

Acrylic *Acrylic::createCompositor()
{
    return Acrylic::createInstance(LIBACRYL_DEFAULT_COMPOSITOR);
}

Acrylic *Acrylic::createScaler()
{
    return Acrylic::createInstance(LIBACRYL_DEFAULT_SCALER);
}

Acrylic *Acrylic::createBlter()
{
    return Acrylic::createInstance(LIBACRYL_DEFAULT_BLTER);
}

Acrylic *AcrylicFactory::createAcrylic(const char *spec)
{
    if (strcmp(spec, "default_compositor") == 0) {
        spec = LIBACRYL_DEFAULT_COMPOSITOR;
    } else if (strcmp(spec, "default_scaler") == 0) {
        spec = LIBACRYL_DEFAULT_SCALER;
    } else if (strcmp(spec, "default_blter") == 0) {
        spec = LIBACRYL_DEFAULT_BLTER;
    }

    return Acrylic::createInstance(spec);
}
