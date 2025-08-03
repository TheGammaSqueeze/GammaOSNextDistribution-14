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

#include <android-base/file.h>
#include <android-base/properties.h>
#include <gtest/gtest.h>
#include <log/log.h>
#include <sys/mman.h>

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string>

TEST(drop_caches, set_perf_property) {
  // Create a 32 MiB file.
  size_t filesize = 33554432;
  // Write 4 KiB sparsely.
  size_t chunksize = 4096;
  char buf[chunksize];
  // Write every 256 KiB.
  size_t blocksize = 262144;

  // fault_around_bytes creates pre-allocated pages that are larger than
  // a standard page. We write chunks of data sparsely across large blocks
  // so that each chunk of data we read back is on a different page, even
  // if they are the larger, pre-allocated ones.
  ALOGI("Allocating %d byte file with %d chunks every %d bytes.",
        static_cast<int>(filesize), static_cast<int>(chunksize),
        static_cast<int>(blocksize));

  android::base::unique_fd fd(
      open("/data/local/tmp/garbage.data", O_CREAT | O_RDWR, 0666));
  ASSERT_NE(-1, fd) << "Failed to allocate a file for the test.";

  for (unsigned int chunk = 0; chunk < filesize / blocksize; chunk++) {
    lseek(fd, chunk * blocksize, SEEK_SET);
    for (unsigned int c = 0; c < chunksize; c++) {
      buf[c] = (random() % 26) + 'A';
    }
    write(fd, buf, chunksize);
  }
  lseek(fd, 0, SEEK_SET);
  ASSERT_NE(-1, fdatasync(fd.get()))
      << "Failed to sync file in memory with storage.";

  // Read the chunks of data created earlier in the file 3 times. The first
  // read promotes these pages to the inactive LRU cache. The second promotes
  // them to the active LRU cache. The third is just for good measure. The
  // next time these pages are read will now be a minor fault.
  for (unsigned int times = 3; times > 0; times--) {
    ssize_t n;
    unsigned int counter = 0;
    while ((n = read(fd.get(), buf, sizeof(buf))) > 0) {
      counter++;
    }
    lseek(fd, 0, SEEK_SET);
  }

  // Read a few bytes from every block while all the data is cached. Every
  // page accessed will cause a minor fault. We later compare this number
  // to the number of major faults from the same operation when the data is
  // not cached.

  void* ptr = mmap(NULL, filesize, PROT_READ, MAP_PRIVATE, fd.get(), 0);
  ASSERT_NE(ptr, MAP_FAILED) << "Failed to mmap the data file.";
  // This advice will prevent readaheads from the OS, which might cause the
  // existing pages in the pagecache to get mapped, reducing the number of
  // minor faults.
  madvise(ptr, filesize, MADV_RANDOM);

  struct rusage usage_before_minor, usage_after_minor;
  getrusage(RUSAGE_SELF, &usage_before_minor);
  for (unsigned int i = 0; i < filesize / blocksize; i++) {
    volatile int tmp = *((char*)ptr + (i * blocksize));
    (void)tmp;  // Bypass the unused error.
  }
  getrusage(RUSAGE_SELF, &usage_after_minor);

  ASSERT_NE(-1, munmap(ptr, filesize)) << "Failed to unmap the data file.";

  android::base::SetProperty("perf.drop_caches", "3");
  // This command can occasionally be delayed from running.
  int attempts_left = 10;
  while (android::base::GetProperty("perf.drop_caches", "-1") != "0") {
    attempts_left--;
    if (attempts_left == 0) {
      FAIL() << "The perf.drop_caches property was never set back to 0. It's "
                "currently equal to"
             << android::base::GetProperty("perf.drop_caches", "") << ".";
    } else {
      sleep(1);
    }
  }

  // Read a few bytes from every block while all the data is not cached.
  // Every page accessed will cause a major fault if the page cache has
  // been dropped like we expect.

  ptr = mmap(NULL, filesize, PROT_READ, MAP_PRIVATE, fd.get(), 0);
  ASSERT_NE(ptr, MAP_FAILED) << "Failed to mmap the data file.";
  // This advice will prevent readaheads from the OS, which may turn major
  // faults into minor faults and obscure whether data was previously cached.
  madvise(ptr, filesize, MADV_RANDOM);

  struct rusage usage_before_major, usage_after_major;
  getrusage(RUSAGE_SELF, &usage_before_major);
  for (unsigned int i = 0; i < filesize / blocksize; i++) {
    volatile int tmp = *((char*)ptr + (i * blocksize));
    (void)tmp;  // Bypass the unused error.
  }
  getrusage(RUSAGE_SELF, &usage_after_major);

  ASSERT_NE(-1, munmap(ptr, filesize)) << "Failed to unmap the data file.";

  long with_cache_minor_faults =
      usage_after_minor.ru_minflt - usage_before_minor.ru_minflt;
  long without_cache_major_faults =
      usage_after_major.ru_majflt - usage_before_major.ru_majflt;

  long with_cache_major_faults =
      usage_after_minor.ru_majflt - usage_before_minor.ru_majflt;
  long without_cache_minor_faults =
      usage_after_major.ru_minflt - usage_before_major.ru_minflt;

  ASSERT_NEAR(with_cache_minor_faults, without_cache_major_faults, 2)
    << "with_cache_major_faults=" << with_cache_major_faults
    << " without_cache_minor_faults=" << without_cache_minor_faults;

  // Try to clean up the garbage.data file from the device.
  remove("/data/local/tmp/garbage.data");
}
