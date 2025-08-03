/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <cstring>
#include <cerrno>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#include <hardware/memtrack.h>

#include "memtrack_exynos.h"

int exynos_memtrack_init(const struct memtrack_module __unused *module)
{
    return 0;
}

int exynos_memtrack_get_memory(const struct memtrack_module __unused *module,
                                pid_t pid,
                                int type,
                                struct memtrack_record *records,
                                size_t *num_records)
{
    struct stat st;

    switch (type) {
        case MEMTRACK_TYPE_GL:
            if (!stat("/d/mali/mem", &st) && S_ISDIR(st.st_mode))
                return mali_memtrack_get_memory(pid, type, records, num_records);
            break;
        case MEMTRACK_TYPE_GRAPHICS:
            if (!stat("/d/ion/clients", &st) && S_ISDIR(st.st_mode))
                return ion_memtrack_get_memory(pid, type, records, num_records);
            [[fallthrough]];
        default:
            return dmabuf_memtrack_get_memory(pid, type, records, num_records);
    }

    return -ENODEV;
}

static struct hw_module_methods_t memtrack_module_methods = {
    .open = NULL,
};

struct memtrack_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = MEMTRACK_MODULE_API_VERSION_0_1,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = MEMTRACK_HARDWARE_MODULE_ID,
        .name = "Exynos Memory Tracker HAL",
        .author = "The Android Open Source Project",
        .methods = &memtrack_module_methods,
    },

    .init = exynos_memtrack_init,
    .getMemory = exynos_memtrack_get_memory,
};

