/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <mali_gpu_utils.h>
#include <string.h>

const int32_t kMaxVersionLength = 64;

int32_t initialize_mali_gpu(const int32_t mali_fd, mali_gpu_info* gpu_info) {
    int32_t ret = 0;
    int32_t version_len = 0;

    // Perform version check handshake
    struct kbase_ioctl_version_check vc = {0};
    ret = ioctl(mali_fd, KBASE_IOCTL_VERSION_CHECK_JM, &vc);
    if (ret < 0) {
        ret = ioctl(mali_fd, KBASE_IOCTL_VERSION_CHECK_CSF, &vc);
        if (ret < 0) {
            printf("Unexpected Mali GPU architecture!");
            return EXIT_FAILURE;
        }
        gpu_info->is_csf = true;
    }

    // Set flags to initialize context
    struct kbase_ioctl_set_flags set_flags = {.create_flags = 0};
    ret = ioctl(mali_fd, KBASE_IOCTL_SET_FLAGS, &set_flags);
    if (ret < 0) {
        printf("Failed to set flags!");
        return EXIT_FAILURE;
    }

    // Map tracking page
    gpu_info->tracking_page =
            mmap(NULL, PAGE_SIZE, PROT_NONE, MAP_SHARED, mali_fd, BASE_MEM_MAP_TRACKING_HANDLE);
    if (gpu_info->tracking_page == MAP_FAILED) {
        printf("Failed to map tracking page!");
        return EXIT_FAILURE;
    }

    // Get KMD version string length
    struct kbase_ioctl_get_ddk_version get_version_len = {
            .version_buffer = 0,
    };
    version_len = ioctl(mali_fd, KBASE_IOCTL_GET_DDK_VERSION, &get_version_len);
    if (version_len < 0) {
        teardown(gpu_info);
        printf("Unexpected KMD version string length!");
        return EXIT_FAILURE;
    }

    // Get KMD version string
    char version_str[kMaxVersionLength];
    memset(version_str, '\0', kMaxVersionLength);
    struct kbase_ioctl_get_ddk_version get_version = {
            .version_buffer = (__u64)&version_str,
            .size = version_len,
    };
    ret = ioctl(mali_fd, KBASE_IOCTL_GET_DDK_VERSION, &get_version);
    if (ret < 0) {
        teardown(gpu_info);
        printf("Unexpected KMD version string!");
        return EXIT_FAILURE;
    }
    version_str[version_len] = '\0';

    // Parse KMD version string with the format `K:r<version>pX-XXXXXX(GPL)`
    sscanf(version_str, "K:r%up", &(gpu_info->version));
    if (gpu_info->version == 0) {
        teardown(gpu_info);
        printf("Parsing failed! Unexpected KMD version string!");
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

void teardown(struct mali_gpu_info* gpu_info) {
    if (!(gpu_info->tracking_page)) {
        munmap(gpu_info->tracking_page, PAGE_SIZE);
        gpu_info->tracking_page = NULL;
    }
}
