/*
 *   Copyright 2020, 2021 Linaro Ltd.
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

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <drm/drm.h>
#include <linux/dma-buf.h>
#include <linux/dma-heap.h>
#include <linux/ion.h>
#include <linux/ion_4.19.h>

#define HEAP_DEVPATH "/dev/dma_heap"
#define ION_DEVPATH "/dev/ion"

#define ONE_MEG (1024 * 1024)
#define NUM_SIZES 4
int sizes[NUM_SIZES] = {4 * 1024, ONE_MEG, 8 * ONE_MEG, 32 * ONE_MEG};

#define NUM_ITERS 5000
#define NSEC_PER_SEC 1000000000LL
#define MAX_HEAP_COUNT ION_HEAP_TYPE_CUSTOM

int ion_heap_open(void) {
    int ret, fd;
    char buf[256];

    ret = sprintf(buf, "%s", ION_DEVPATH);
    if (ret < 0) {
        printf("sprintf failed!\n");
        return ret;
    }

    fd = open(buf, O_RDWR);
    if (fd < 0) printf("open %s failed!\n", buf);
    return fd;
}

int ion_heap_alloc(int ionfd, int heap_id, size_t len, unsigned int flags, int* dmabuf_fd) {
    struct ion_new_allocation_data alloc_data;
    int ret;

    memset(&alloc_data, 0, sizeof(alloc_data));
    alloc_data.heap_id_mask = 1 << heap_id;
    alloc_data.flags = flags;
    alloc_data.len = len;

    /* Allocate memory for this ION client as per heap_type */
    ret = ioctl(ionfd, ION_IOC_NEW_ALLOC, &alloc_data);

    *dmabuf_fd = alloc_data.fd;

    return ret;
}

int dmabuf_heap_open(char* name) {
    int ret, fd;
    char buf[256];

    ret = sprintf(buf, "%s/%s", HEAP_DEVPATH, name);
    if (ret < 0) {
        printf("sprintf failed!\n");
        return ret;
    }

    fd = open(buf, O_RDWR);
    if (fd < 0) printf("open %s failed!\n", buf);
    return fd;
}

int dmabuf_heap_alloc(int fd, size_t len, unsigned int flags, int* dmabuf_fd) {
    struct dma_heap_allocation_data data = {
            .len = len,
            .fd_flags = O_RDWR | O_CLOEXEC,
            .heap_flags = flags,
    };
    int ret;

    if (dmabuf_fd == NULL) return -EINVAL;

    ret = ioctl(fd, DMA_HEAP_IOCTL_ALLOC, &data);
    if (ret < 0) return ret;
    *dmabuf_fd = (int)data.fd;
    return ret;
}

void dmabuf_sync(int fd, int start_stop) {
    struct dma_buf_sync sync = {0};
    int ret;

    sync.flags = start_stop | DMA_BUF_SYNC_RW;
    ret = ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync);
    if (ret) printf("sync failed %d\n", errno);
}

void ion_heap_bench(unsigned int heap_type, int size, int flags) {
    int heap_id;
    int ionfd = -1, dmabuf_fd = -1;
    struct ion_heap_query query;
    struct ion_heap_data heap_data[MAX_HEAP_COUNT];
    struct timespec ts_start, ts_end;
    long long start, end;
    int ret;
    unsigned int i;

    ionfd = ion_heap_open();
    if (ionfd < 0) return;

    memset(&query, 0, sizeof(query));
    query.cnt = MAX_HEAP_COUNT;
    query.heaps = (unsigned long int)&heap_data[0];
    /* Query ION heap_id_mask from ION heap */
    ret = ioctl(ionfd, ION_IOC_HEAP_QUERY, &query);
    if (ret < 0) {
        printf("<%s>: Failed: ION_IOC_HEAP_QUERY: %s\n", __func__, strerror(errno));
        goto out;
    }
    heap_id = MAX_HEAP_COUNT + 1;
    for (i = 0; i < query.cnt; i++) {
        if (heap_data[i].type == heap_type) {
            heap_id = heap_data[i].heap_id;
            break;
        }
    }
    if (heap_id > MAX_HEAP_COUNT) {
        printf("<%s>: ERROR: heap type does not exists\n", __func__);
        goto out;
    }

    clock_gettime(CLOCK_MONOTONIC, &ts_start);
    for (i = 0; i < NUM_ITERS; i++) {
        ret = ion_heap_alloc(ionfd, heap_id, size, flags, &dmabuf_fd);
        if (ret) goto out;
        close(dmabuf_fd);
    }
    clock_gettime(CLOCK_MONOTONIC, &ts_end);

    start = ts_start.tv_sec * NSEC_PER_SEC + ts_start.tv_nsec;
    end = ts_end.tv_sec * NSEC_PER_SEC + ts_end.tv_nsec;

    printf("ion heap:    alloc %d bytes %i times in %lld ns \t %lld ns/call\n", size, NUM_ITERS,
           end - start, (end - start) / NUM_ITERS);
out:
    if (ionfd >= 0) close(ionfd);
}

void dmabuf_heap_bench(char* heap_name, int size) {
    int heap_fd = -1, dmabuf_fd = -1;
    struct timespec ts_start, ts_end;
    long long start, end;
    int ret;
    int i;

    heap_fd = dmabuf_heap_open(heap_name);
    if (heap_fd < 0) return;

    clock_gettime(CLOCK_MONOTONIC, &ts_start);
    for (i = 0; i < NUM_ITERS; i++) {
        ret = dmabuf_heap_alloc(heap_fd, size, 0, &dmabuf_fd);
        if (ret) goto out;
        close(dmabuf_fd);
    }
    clock_gettime(CLOCK_MONOTONIC, &ts_end);

    start = ts_start.tv_sec * NSEC_PER_SEC + ts_start.tv_nsec;
    end = ts_end.tv_sec * NSEC_PER_SEC + ts_end.tv_nsec;

    printf("dmabuf heap: alloc %d bytes %i times in %lld ns \t %lld ns/call\n", size, NUM_ITERS,
           end - start, (end - start) / NUM_ITERS);
out:
    if (heap_fd >= 0) close(heap_fd);
}

int main(int argc, char* argv[]) {
    char* dmabuf_heap_name;
    unsigned int ion_heap_type;
    int ion_flags = 0;
    int testing_ion = 0;
    int i;
    if (argc < 2) {
        printf("Usage %s [-i <ion heap type> <ion heap flags>] <dmabuf heap name>\n", argv[0]);
        return -1;
    }

    if (argv[1][0] == '-' && argv[1][1] == 'i') {
        testing_ion = 1;
        ion_heap_type = strtol(argv[2], NULL, 0);
        ion_flags = strtol(argv[3], NULL, 0);
        dmabuf_heap_name = argv[4];
    } else {
        dmabuf_heap_name = argv[1];
    }

    printf("Testing dmabuf %s", dmabuf_heap_name);
    if (testing_ion) printf(" vs ion heaptype %d (flags: 0x%x)", ion_heap_type, ion_flags);
    printf("\n---------------------------------------------\n");
    for (i = 0; i < NUM_SIZES; i++) {
        dmabuf_heap_bench(dmabuf_heap_name, sizes[i]);
        if (testing_ion) ion_heap_bench(ion_heap_type, sizes[i], ion_flags);
    }

    return 0;
}
