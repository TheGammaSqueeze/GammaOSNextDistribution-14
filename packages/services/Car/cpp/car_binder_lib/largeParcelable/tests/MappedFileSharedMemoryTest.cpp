/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <cutils/ashmem.h>
#include <gtest/gtest.h>

#include <MappedFile.h>
#include <SharedMemory.h>
#include <errno.h>
#include <sys/mman.h>

#include <memory>

namespace {

using ::android::OK;
using ::android::automotive::car_binder_lib::MappedFile;
using ::android::automotive::car_binder_lib::SharedMemory;
using ::android::base::borrowed_fd;
using ::android::base::unique_fd;

constexpr size_t TEST_SIZE = 1024;

TEST(MappedFileSharedMemoryTest, testSharedMemoryInvalidFd) {
    unique_fd fd(-1);
    SharedMemory sm(std::move(fd));

    ASSERT_FALSE(sm.isValid());
    ASSERT_NE(OK, sm.getErr());
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryInvalidAshmemUniqueFd) {
    unique_fd fd(0);
    SharedMemory sm(std::move(fd));

    ASSERT_FALSE(sm.isValid());
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryInvalidAshmemBorrowedFd) {
    borrowed_fd fd(0);
    SharedMemory sm(std::move(fd));

    ASSERT_FALSE(sm.isValid());
}

void testSharedMemoryMapRead(const SharedMemory& sm) {
    std::unique_ptr<MappedFile> mappedFile = sm.mapReadOnly();

    ASSERT_TRUE(mappedFile->isValid());
    ASSERT_EQ(OK, mappedFile->getErr());
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryWithFdMapRead) {
    unique_fd fd(ashmem_create_region("SharedMemory", TEST_SIZE));
    ASSERT_TRUE(fd.ok());
    SharedMemory sm(std::move(fd));

    ASSERT_TRUE(sm.isValid());
    ASSERT_EQ(OK, sm.getErr());

    testSharedMemoryMapRead(sm);
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryWithSizeMapRead) {
    SharedMemory sm(TEST_SIZE);

    ASSERT_TRUE(sm.isValid());
    ASSERT_EQ(OK, sm.getErr());

    testSharedMemoryMapRead(sm);
}

void testSharedMemoryMapWriteAndRead(const SharedMemory& sm) {
    std::unique_ptr<MappedFile> writeFile = sm.mapReadWrite();

    ASSERT_TRUE(writeFile->isValid());
    ASSERT_EQ(OK, writeFile->getErr());

    // Write something into the file.
    uint8_t buffer[TEST_SIZE];
    memset(buffer, 0xff, sizeof(buffer));
    void* addr = writeFile->getWriteAddr();
    memcpy(addr, buffer, sizeof(buffer));
    writeFile->sync();

    // Try to read using a readonly map.
    std::unique_ptr<MappedFile> readFile = sm.mapReadOnly();

    ASSERT_TRUE(readFile->isValid());
    ASSERT_EQ(OK, readFile->getErr());

    const void* readAddr = readFile->getAddr();

    ASSERT_EQ(0, memcmp(readAddr, buffer, sizeof(buffer)));
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryWithOwnedFdMapWriteAndRead) {
    unique_fd fd(ashmem_create_region("SharedMemory", TEST_SIZE));
    ASSERT_TRUE(fd.ok());
    SharedMemory sm(std::move(fd));

    ASSERT_TRUE(sm.isValid());
    ASSERT_EQ(OK, sm.getErr());

    testSharedMemoryMapWriteAndRead(sm);
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryWithSizeMapWriteAndRead) {
    SharedMemory sm(TEST_SIZE);

    ASSERT_TRUE(sm.isValid());
    ASSERT_EQ(OK, sm.getErr());

    testSharedMemoryMapWriteAndRead(sm);
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryWithBorrowedFdMapWriteAndRead) {
    unique_fd fd(ashmem_create_region("SharedMemory", TEST_SIZE));
    ASSERT_TRUE(fd.ok());

    borrowed_fd bfd(fd);
    SharedMemory sm(bfd);

    ASSERT_TRUE(sm.isValid());
    ASSERT_EQ(OK, sm.getErr());

    testSharedMemoryMapWriteAndRead(sm);
}

TEST(MappedFileSharedMemoryTest, testSharedMemoryLock) {
    SharedMemory sm(TEST_SIZE);

    std::unique_ptr<MappedFile> writeFile = sm.mapReadWrite();

    ASSERT_TRUE(writeFile->isValid());

    sm.lock();

    std::unique_ptr<MappedFile> readFile = sm.mapReadOnly();

    ASSERT_TRUE(readFile->isValid());

    // sm.mapReadWrite() after lock would panic.
    int fd = sm.getFd();
    void* addr = mmap(NULL, TEST_SIZE, PROT_WRITE, MAP_SHARED, fd, 0);

    ASSERT_EQ(addr, MAP_FAILED);
}

}  //  namespace
