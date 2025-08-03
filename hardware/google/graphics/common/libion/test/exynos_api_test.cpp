/*
 * Copyright (C) 2018 Samsung Electronics Co., Ltd.
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

#include <memory>
#include <sys/mman.h>

#include <gtest/gtest.h>

#include <hardware/exynos/ion.h>
#include "ion_test_fixture.h"

class ExynosApiTest : public IonAllHeapsTest {
};

TEST_F(ExynosApiTest, ApiTest)
{
    static const size_t allocationSizes[] = {4*1024, 64*1024, 1024*1024, 2*1024*1024};
    static const unsigned int exynosHeaps[] = {
        EXYNOS_ION_HEAP_SYSTEM_MASK,
        EXYNOS_ION_HEAP_CRYPTO_MASK,
        EXYNOS_ION_HEAP_VIDEO_STREAM_MASK,
        EXYNOS_ION_HEAP_CAMERA,
    };

    for (unsigned int heapMask : exynosHeaps) {
        for (size_t size : allocationSizes) {
            SCOPED_TRACE(::testing::Message() << "heap " << heapMask);
            SCOPED_TRACE(::testing::Message() << "size " << size);
            int map_fd = -1;
            ASSERT_EQ(0, exynos_ion_alloc(m_ionFd, size, heapMask, 0, &map_fd));
            ASSERT_GE(map_fd, 0);
            ASSERT_EQ(0, close(map_fd));
        }
    }
}
