/*
 *  dmabuf_container.c
 *
 *   Copyright 2018 Samsung Electronics Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#define LOG_TAG "dmabuf-container"

#include <string.h>
#include <errno.h>
#include <sys/ioctl.h>

#include <string.h>

#include <sys/ioctl.h>

#include <log/log.h>

#include <hardware/exynos/ion.h>
#include <hardware/exynos/dmabuf_container.h>

struct dma_buf_merge {
    int      *dma_bufs;
    int32_t  count;
    int32_t  dmabuf_container;
    uint32_t reserved[2];
};
#define DMA_BUF_BASE        'b'
#define DMA_BUF_IOCTL_MERGE                 _IOWR(DMA_BUF_BASE, 13, struct dma_buf_merge)
#define DMA_BUF_IOCTL_CONTAINER_SET_MASK    _IOW(DMA_BUF_BASE, 14, __u32)
#define DMA_BUF_IOCTL_CONTAINER_GET_MASK    _IOR(DMA_BUF_BASE, 14, __u32)

int dma_buf_merge(int base_fd, int src_fds[], int src_count)
{
    struct dma_buf_merge data = {
        .dma_bufs = src_fds,
        .count = src_count,
    };

    if (src_count > MAX_BUFCON_SRC_BUFS) {
        ALOGE("too many source buffers");
        return -1;
    }

    if (ioctl(base_fd, DMA_BUF_IOCTL_MERGE, &data) < 0) {
        ALOGE("failed to merge %d dma-bufs: %s", src_count, strerror(errno));
        return -1;
    }

    return data.dmabuf_container;
}

int dmabuf_container_set_mask(int dmabuf, uint32_t mask)
{
    if (ioctl(dmabuf, DMA_BUF_IOCTL_CONTAINER_SET_MASK, &mask) < 0) {
        ALOGE("Failed to configure dma-buf container mask %#x: %s", mask, strerror(errno));
        return -1;
    }

    return 0;
}

int dmabuf_container_get_mask(int dmabuf, uint32_t *mask)
{
    if (ioctl(dmabuf, DMA_BUF_IOCTL_CONTAINER_GET_MASK, mask) < 0) {
        ALOGE("Failed to retrieve dma-buf container mask: %s", strerror(errno));
        return -1;
    }

    return 0;
}
