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

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <log/log.h>

#include <hardware/memtrack.h>

#include "memtrack_exynos.h"

#define ARRAY_SIZE(x) (sizeof(x)/sizeof(x[0]))
#define min(x, y) ((x) < (y) ? (x) : (y))

#define ION_DEBUG_PATH          "/sys/kernel/debug/ion/"
#define ION_DEBUG_CLIENT_PATH   "/sys/kernel/debug/ion/clients"

static struct memtrack_record ion_record_templates[] = {
    {
        .flags = MEMTRACK_FLAG_SMAPS_UNACCOUNTED |
                 MEMTRACK_FLAG_PRIVATE |
                 MEMTRACK_FLAG_SYSTEM |
                 MEMTRACK_FLAG_NONSECURE,
    },
};

int ion_memtrack_get_memory(pid_t pid, int __unused type,
                             struct memtrack_record *records,
                             size_t *num_records)
{
    size_t allocated_records = min(*num_records, ARRAY_SIZE(ion_record_templates));
    FILE *fp;
    struct stat s;
    char line[1024];
    char path[64];

    *num_records = ARRAY_SIZE(ion_record_templates);

    /* fastpath to return the necessary number of records */
    if (allocated_records == 0) {
        return 0;
    }

    if (lstat(ION_DEBUG_CLIENT_PATH, &s) == 0)
        snprintf(path, sizeof(path), "%s/%d-0", ION_DEBUG_CLIENT_PATH, pid);
    else
        snprintf(path, sizeof(path), "%s/%d", ION_DEBUG_PATH, pid);

    fp = fopen(path, "r");
    if (fp == NULL) {
        return -errno;
    }

    memcpy(records, ion_record_templates,
           sizeof(struct memtrack_record) * allocated_records);

    while (1) {
        if (fgets(line, sizeof(line), fp) == NULL) {
            break;
        }

        /* Format:
         *        heap_name:    size_in_bytes
         * ion_noncontig_he:         30134272
         */
        if (!strncmp(line, "ion_noncontig_he", 16)) {
            unsigned int size_in_bytes;

            int ret = sscanf(line, "%*s %u\n", &size_in_bytes);
            if (ret == 1) {
                    records[0].size_in_bytes = size_in_bytes;
                    break;
            }
        }
    }

    fclose(fp);

    return 0;
}
