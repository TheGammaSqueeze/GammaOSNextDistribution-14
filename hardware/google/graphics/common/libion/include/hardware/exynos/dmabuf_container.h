/*
 *  hardware/exynos/dmabuf_container.h
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

#ifndef __HARDWARE_EXYNOS_DMABUF_CONTAINER_H__
#define __HARDWARE_EXYNOS_DMABUF_CONTAINER_H__

#define MAX_BUFCON_BUFS 32
#define MAX_BUFCON_SRC_BUFS (MAX_BUFCON_BUFS - 1)

__BEGIN_DECLS

int dma_buf_merge(int base_fd, int src_fds[], int src_count);
int dmabuf_container_set_mask(int dmabuf, uint32_t mask);
int dmabuf_container_get_mask(int dmabuf, uint32_t *mask);

__END_DECLS

#endif /* __HARDWARE_EXYNOS_DMABUF_CONTAINER_H__ */
