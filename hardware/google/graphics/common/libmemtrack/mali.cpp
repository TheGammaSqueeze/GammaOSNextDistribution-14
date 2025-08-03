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
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <log/log.h>

#include <hardware/memtrack.h>

#include "memtrack_exynos.h"

/* Following includes added for directory parsing. */
#include <sys/types.h>
#include <dirent.h>

/* Some general defines. */
#define MALI_DEBUG_FS_PATH 		"/d/mali/mem/"
#define MALI_DEBUG_MEM_FILE		"/mem_profile"

#define MAX_FILES_PER_PID 		8
#define MAX_FILES_PER_NAME		32
static int libmemtrack_gbl_input_filename_counter = 	0;
static char libmemtrack_gbl_input_filename[MAX_FILES_PER_PID][MAX_FILES_PER_NAME];

#define ARRAY_SIZE(x) (sizeof(x)/sizeof(x[0]))
#define min(x, y) ((x) < (y) ? (x) : (y))

struct memtrack_record record_templates[] = {
    {
        .flags = MEMTRACK_FLAG_SMAPS_ACCOUNTED |
                 MEMTRACK_FLAG_PRIVATE |
                 MEMTRACK_FLAG_NONSECURE,
    },
    {
        .flags = MEMTRACK_FLAG_SMAPS_UNACCOUNTED |
                 MEMTRACK_FLAG_PRIVATE |
                 MEMTRACK_FLAG_NONSECURE,
    },
};

static void scan_directory_for_filenames(pid_t pid)
{
    /* As per ARM, there can be multiple files */
    DIR *directory;
    struct dirent *entries;
    char pid_string[12] = {0};
    int pid_index = 0;

    /* Open directory. */
    directory = opendir(MALI_DEBUG_FS_PATH);

    sprintf(pid_string, "%d", pid);
    for (pid_index = 0; pid_index < 12; pid_index++) {
        if (pid_string[pid_index] == 0) {
            pid_string[pid_index] = '_';
            break;
        }
    }

    libmemtrack_gbl_input_filename_counter = 0;

    if (directory != NULL) {
        /* Keep reading the directory. */
        while ((entries = readdir(directory))) {
            /* Check if the PID is present in the direcotry entry.
             * If it is present, then keep the filename for reading
             * contents. Also concatenate the directory path, so that
             * file can be opened.
             * */
            if (!strncmp(entries->d_name, pid_string, strlen(pid_string))) {
                snprintf(&libmemtrack_gbl_input_filename[libmemtrack_gbl_input_filename_counter][0], MAX_FILES_PER_NAME, "%s%s%s", MALI_DEBUG_FS_PATH, entries->d_name, MALI_DEBUG_MEM_FILE);
                libmemtrack_gbl_input_filename_counter++;
            }
        }
        /* Close directory before leaving. */
        (void) closedir(directory);
    } else {
        ALOGE("libmemtrack-hw -- Couldn't open the directory - %s \r\n", MALI_DEBUG_FS_PATH);
    }

    return;
}

int mali_memtrack_get_memory(pid_t pid, int __unused type,
                             struct memtrack_record *records,
                             size_t *num_records)
{
    size_t allocated_records = min(*num_records, ARRAY_SIZE(record_templates));
    FILE *fp;
    int local_count;
    long long int temp_val = 0, total_memory_size = 0, native_buf_mem_size = 0;
    bool native_buffer_read = false;
    char line[1024] = {0};

    *num_records = ARRAY_SIZE(record_templates);

    /* fastpath to return the necessary number of records */
    if (allocated_records == 0) {
        return 0;
    }

    memcpy(records, record_templates,
           sizeof(struct memtrack_record) * allocated_records);

    /* First, scan the directoy. */
    scan_directory_for_filenames(pid);

    local_count = 0;
    total_memory_size = 0;
    native_buf_mem_size = 0;

    while (local_count < libmemtrack_gbl_input_filename_counter) {
        fp = fopen(&libmemtrack_gbl_input_filename[local_count][0], "r");

        if (fp == NULL) {
            /* Unable to open the file. Either move to next file, or
             * return to caller. */
            local_count++;
            continue;
        }

        while (1) {
            char memory_type[16] = {0};
            char memory_type_2[16] = {0};
            int ret = 0;

            if (native_buffer_read == false) {
                if (fgets(line, sizeof(line), fp) == NULL) {
                    if (native_buffer_read == false) {
                        /* Unable to read Native buffer.
                         * Probably DDK doesn't support Native Buffer.
                         * Reset to start of file and look for Total
                         * Memory. */
                        fseek(fp, 0, SEEK_SET);

                        /* Also, set Native buffer flag and memory sizes. */
                        native_buffer_read = true;
                        continue;
                    }
                }

                /* Search for Total memory. */
                /* Format:
                 *
                 * Channel: Native Buffer (Total memory: 44285952)
                 *
                 */
                ret = sscanf(line, "%*s %15s %15s %*s %*s %lld \n", memory_type, memory_type_2, &temp_val);

                if (ret != 3)
                    continue;

                if ((strcmp(memory_type, "Native") == 0) &&
                        (strcmp(memory_type_2, "Buffer") == 0)) {
                    /* Set native buffer memory read flag to true. */
                    native_buffer_read = true;
                    if ((INT64_MAX - temp_val) > native_buf_mem_size)
                        native_buf_mem_size += temp_val;
                    else
                        native_buf_mem_size = INT64_MAX;
                } else {
                    /* Ignore case. Nothing to do here. */
                    /* Continue reading file until NativeBuffer is found. */
                }
            } else {
                if (fgets(line, sizeof(line), fp) == NULL) {
                    /* Unable to find, so break the loop here.*/
                    break;
                }

                /* Search for Total memory. */
                /* Format:
                 *
                 * Total allocated memory: 36146960
                 *
                 */
                ret = sscanf(line, "%15s %*s %*s %lld \n", memory_type, &temp_val);

                if (ret != 2)
                    continue;

                if (strcmp(memory_type, "Total") == 0) {
                    /* Store total memory. */
                    if ((INT64_MAX - temp_val) > total_memory_size)
                        total_memory_size += temp_val;
                    else
                        total_memory_size = INT64_MAX;
                } else {
                    /* Ignore case. Nothing to do here. */
                }
            } /* end if (native_buffer_read == false) */

        } /* End while(1) */

        if (fp != NULL) {
            /* Close the opened file. */
            fclose(fp);

            /* Reset some of the local variables here. */
            fp = NULL;
            native_buffer_read = false;
        }

        /* Manage local variables and counters. */
        local_count++;

    } /* while (local_count <= libmemtrack_gbl_input_filename_counter) */

    /* Arrange and return memory size details. */
    if (allocated_records > 0)
        records[0].size_in_bytes = 0;

    if (allocated_records > 1)
    {
        if (native_buf_mem_size >= 0 && total_memory_size > native_buf_mem_size)
            records[1].size_in_bytes = total_memory_size - native_buf_mem_size;
        else
            records[1].size_in_bytes = 0;
    }

    return 0;
}
